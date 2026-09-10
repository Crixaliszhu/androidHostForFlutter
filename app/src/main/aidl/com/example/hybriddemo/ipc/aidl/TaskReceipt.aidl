package com.example.hybriddemo.ipc.aidl;

// 任务受理结果显式传递业务错误码，避免依赖 Android 非公开异常类型。
parcelable TaskReceipt;
