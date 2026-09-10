package com.example.hybriddemo.ipc.aidl;

import android.os.Parcel;
import android.os.Parcelable;

/** 任务提交的结构化回执；code 为零表示受理，不代表任务已经完成。 */
public final class TaskReceipt implements Parcelable {
    public final int code;
    public final String message;

    public TaskReceipt(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(code);
        dest.writeString(message);
    }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<TaskReceipt> CREATOR = new Creator<TaskReceipt>() {
        @Override
        public TaskReceipt createFromParcel(Parcel source) {
            return new TaskReceipt(source.readInt(), source.readString());
        }

        @Override
        public TaskReceipt[] newArray(int size) { return new TaskReceipt[size]; }
    };
}
