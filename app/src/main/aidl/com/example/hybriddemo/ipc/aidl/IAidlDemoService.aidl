package com.example.hybriddemo.ipc.aidl;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import com.example.hybriddemo.ipc.aidl.IpcRecord;
import com.example.hybriddemo.ipc.aidl.TaskReceipt;
import com.example.hybriddemo.ipc.aidl.IAidlDemoCallback;

// 仅供同一应用跨进程使用；方法事务编号固定，新增方法使用新的编号。
interface IAidlDemoService {
    const int PROTOCOL_VERSION = 1;
    const int STATUS_OK = 0;
    const int STATUS_CANCELLED = 1;
    const int ERROR_INVALID_ARGUMENT = 1001;
    const int ERROR_NOT_REGISTERED = 1002;
    const int ERROR_DUPLICATE_REQUEST = 1003;
    const int ERROR_BUSY = 1004;

    Bundle getServiceInfo() = 1;
    int add(int a, int b) = 2;
    @nullable IpcRecord echo(in @nullable IpcRecord record) = 3;
    List<IpcRecord> getRecords(int offset, int limit) = 4;
    void demonstrateDirections(in IpcRecord input, out IpcRecord output,
                               inout IpcRecord both) = 5;
    long incrementCounter(int delta) = 6;
    void registerCallback(IAidlDemoCallback callback) = 7;
    void unregisterCallback(IAidlDemoCallback callback) = 8;
    // 同步返回只表示任务已受理，最终结果通过 oneway 回调返回。
    TaskReceipt submitTask(IAidlDemoCallback callback, String requestId, long delayMillis) = 9;
    boolean cancelTask(IAidlDemoCallback callback, String requestId) = 10;
    // 大数据通过只读文件描述符传输，避免占用 Binder 事务缓冲区。
    ParcelFileDescriptor openPayload() = 11;
    // 仅 Debug 允许；延迟终止当前远程进程，让客户端观察死亡及重连。
    void simulateProcessDeath() = 12;
}
