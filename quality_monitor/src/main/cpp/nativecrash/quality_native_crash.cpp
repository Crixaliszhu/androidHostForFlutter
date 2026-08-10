#include <jni.h>
#include <android/log.h>
#include <csignal>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/ucontext.h>
#include <sys/wait.h>
#include <unistd.h>

namespace {

// 第一阶段只处理常见致命信号；Java/Kotlin 崩溃由上层 UncaughtExceptionHandler 负责。
constexpr int kHandledSignals[] = {SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGTRAP};
constexpr size_t kMaxPath = 512;
// 替代信号栈用于栈溢出等极端场景，避免原线程栈已经损坏时无法进入处理器。
constexpr size_t kAltStackSize = 64 * 1024;

char g_tombstone_dir[kMaxPath] = {0};
stack_t g_old_stack{};
uint8_t g_alt_stack[kAltStackSize] = {0};
struct sigaction g_old_handlers[NSIG]{};

// 信号处理路径只使用底层 write，避免 stdio 缓冲、锁和堆分配带来二次崩溃风险。
void write_text(int fd, const char *text) {
    if (text == nullptr) return;
    write(fd, text, strlen(text));
}

// 统一格式化少量 tombstone 字段；buffer 固定在栈上，避免崩溃现场申请堆内存。
void write_format(int fd, const char *format, ...) {
    char buffer[1024];
    va_list args;
    va_start(args, format);
    int len = vsnprintf(buffer, sizeof(buffer), format, args);
    va_end(args);
    if (len > 0) {
        write(fd, buffer, static_cast<size_t>(len < static_cast<int>(sizeof(buffer)) ? len : sizeof(buffer) - 1));
    }
}

// 将信号编号转换成可读名称，方便服务端和本地日志直接按信号类型聚合。
const char *signal_name(int sig) {
    switch (sig) {
        case SIGSEGV: return "SIGSEGV";
        case SIGABRT: return "SIGABRT";
        case SIGBUS: return "SIGBUS";
        case SIGILL: return "SIGILL";
        case SIGFPE: return "SIGFPE";
        case SIGTRAP: return "SIGTRAP";
        default: return "UNKNOWN";
    }
}

// 读取 /proc/self/maps，后续服务端符号化需要用它把 pc 地址映射到 so 和偏移。
void copy_file_to_fd(int out_fd, const char *path) {
    int in_fd = open(path, O_RDONLY | O_CLOEXEC);
    if (in_fd < 0) return;
    char buffer[4096];
    ssize_t read_size;
    while ((read_size = read(in_fd, buffer, sizeof(buffer))) > 0) {
        write(out_fd, buffer, static_cast<size_t>(read_size));
    }
    close(in_fd);
}

// 粗略校验 frame pointer 是否仍落在线程栈附近，降低坏栈导致无限遍历的概率。
bool likely_stack_pointer(uintptr_t value, uintptr_t stack_pointer) {
    return value > stack_pointer && value < stack_pointer + 8 * 1024 * 1024 && value % sizeof(uintptr_t) == 0;
}

// 基于寄存器中的 fp/lr 和栈帧链回溯崩溃线程；这是增强版第一阶段的本地兜底回溯。
void dump_frame_pointer_backtrace(int fd, ucontext_t *context) {
#if defined(__aarch64__)
    uintptr_t pc = static_cast<uintptr_t>(context->uc_mcontext.pc);
    uintptr_t sp = static_cast<uintptr_t>(context->uc_mcontext.sp);
    uintptr_t fp = static_cast<uintptr_t>(context->uc_mcontext.regs[29]);
    uintptr_t lr = static_cast<uintptr_t>(context->uc_mcontext.regs[30]);
    write_format(fd, "registers:\n  pc: 0x%lx\n  sp: 0x%lx\n  fp(x29): 0x%lx\n  lr(x30): 0x%lx\n\n",
                 pc, sp, fp, lr);
    write_text(fd, "backtrace by frame pointer:\n");
    write_format(fd, "  #00 pc 0x%lx\n", pc);
    write_format(fd, "  #01 pc 0x%lx\n", lr);
    uintptr_t current_fp = fp;
    for (int index = 2; index < 64 && likely_stack_pointer(current_fp, sp); index++) {
        auto *frame = reinterpret_cast<uintptr_t *>(current_fp);
        uintptr_t next_fp = frame[0];
        uintptr_t return_address = frame[1];
        if (return_address == 0 || next_fp <= current_fp) break;
        write_format(fd, "  #%02d pc 0x%lx fp 0x%lx\n", index, return_address, current_fp);
        current_fp = next_fp;
    }
#elif defined(__x86_64__)
    uintptr_t pc = static_cast<uintptr_t>(context->uc_mcontext.gregs[REG_RIP]);
    uintptr_t sp = static_cast<uintptr_t>(context->uc_mcontext.gregs[REG_RSP]);
    uintptr_t fp = static_cast<uintptr_t>(context->uc_mcontext.gregs[REG_RBP]);
    write_format(fd, "registers:\n  pc: 0x%lx\n  sp: 0x%lx\n  fp(rbp): 0x%lx\n\n", pc, sp, fp);
    write_text(fd, "backtrace by frame pointer:\n");
    write_format(fd, "  #00 pc 0x%lx\n", pc);
    uintptr_t current_fp = fp;
    for (int index = 1; index < 64 && likely_stack_pointer(current_fp, sp); index++) {
        auto *frame = reinterpret_cast<uintptr_t *>(current_fp);
        uintptr_t next_fp = frame[0];
        uintptr_t return_address = frame[1];
        if (return_address == 0 || next_fp <= current_fp) break;
        write_format(fd, "  #%02d pc 0x%lx fp 0x%lx\n", index, return_address, current_fp);
        current_fp = next_fp;
    }
#else
    write_text(fd, "backtrace by frame pointer: unsupported abi in this MVP\n");
#endif
}

// 子进程执行真正 dump，避免在即将崩溃的父进程里做大量 I/O 和格式化工作。
void dump_tombstone_child(int sig, siginfo_t *info, void *user_context) {
    char path[kMaxPath];
    timespec ts{};
    clock_gettime(CLOCK_REALTIME, &ts);
    snprintf(path, sizeof(path), "%s/native_%lld_%d.qmon",
             g_tombstone_dir,
             static_cast<long long>(ts.tv_sec) * 1000 + ts.tv_nsec / 1000000,
             getpid());

    int fd = open(path, O_CREAT | O_WRONLY | O_TRUNC | O_CLOEXEC, 0600);
    if (fd < 0) _exit(128 + sig);

    write_text(fd, "quality native crash tombstone\n");
    write_format(fd, "pid: %d\n", getppid());
    write_format(fd, "dumper_pid: %d\n", getpid());
    write_format(fd, "signal: %s(%d)\n", signal_name(sig), sig);
    write_format(fd, "code: %d\n", info != nullptr ? info->si_code : 0);
    write_format(fd, "fault_addr: %p\n\n", info != nullptr ? info->si_addr : nullptr);

    if (user_context != nullptr) {
        dump_frame_pointer_backtrace(fd, reinterpret_cast<ucontext_t *>(user_context));
    }

    write_text(fd, "\n\nmaps:\n");
    copy_file_to_fd(fd, "/proc/self/maps");
    close(fd);
    _exit(128 + sig);
}

// 父进程收到致命信号后 fork dumper 子进程，随后恢复原 handler 并重新抛出信号。
void crash_signal_handler(int sig, siginfo_t *info, void *user_context) {
    pid_t child = fork();
    if (child == 0) {
        dump_tombstone_child(sig, info, user_context);
    }

    sigaction(sig, &g_old_handlers[sig], nullptr);
    raise(sig);
}

// 安装 sigaction 和替代信号栈；必须在 App 启动早期完成，才能覆盖后续 native 崩溃。
void install_signal_handlers() {
    stack_t stack{};
    stack.ss_sp = g_alt_stack;
    stack.ss_size = sizeof(g_alt_stack);
    stack.ss_flags = 0;
    sigaltstack(&stack, &g_old_stack);

    struct sigaction action{};
    memset(&action, 0, sizeof(action));
    sigemptyset(&action.sa_mask);
    action.sa_sigaction = crash_signal_handler;
    action.sa_flags = SA_SIGINFO | SA_ONSTACK;

    for (int sig : kHandledSignals) {
        sigaction(sig, &action, &g_old_handlers[sig]);
    }
}

} // 匿名命名空间

// JNI 初始化入口：保存 tombstone 目录并安装 native 崩溃信号处理器。
extern "C" JNIEXPORT void JNICALL
Java_com_example_qualitymonitor_crash_nativecrash_NativeCrashCollector_00024NativeCrashBridge_nativeInit(
        JNIEnv *env,
        jobject,
        jstring tombstone_dir) {
    const char *dir = env->GetStringUTFChars(tombstone_dir, nullptr);
    if (dir != nullptr) {
        snprintf(g_tombstone_dir, sizeof(g_tombstone_dir), "%s", dir);
        mkdir(g_tombstone_dir, 0700);
        env->ReleaseStringUTFChars(tombstone_dir, dir);
    }
    install_signal_handlers();
}

// Demo 测试入口：主动触发空指针写入，用于验证 native tombstone 是否正常落盘。
extern "C" JNIEXPORT void JNICALL
Java_com_example_qualitymonitor_crash_nativecrash_NativeCrashCollector_00024NativeCrashBridge_nativeCrashForTest(
        JNIEnv *,
        jobject) {
    volatile int *ptr = nullptr;
    *ptr = 1;
}
