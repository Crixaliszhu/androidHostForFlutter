package com.example.hybriddemo.ipc.aidl;

import com.example.hybriddemo.ipc.aidl.IpcRecord;

// 回调使用 oneway，服务端不等待客户端业务处理；客户端仍须切回主线程更新 UI。
oneway interface IAidlDemoCallback {
    void onTaskFinished(String requestId, int status, in IpcRecord result);
    void onCounterChanged(long value);
}
