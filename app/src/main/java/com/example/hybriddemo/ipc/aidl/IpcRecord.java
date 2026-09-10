package com.example.hybriddemo.ipc.aidl;

import android.os.Parcel;
import android.os.Parcelable;

/** AIDL 示例传输对象；out/inout 要求无参构造和原地更新的 readFromParcel。 */
public final class IpcRecord implements Parcelable {
    public int id;
    public String text;
    public long value;

    public IpcRecord() { }

    public IpcRecord(int id, String text, long value) {
        this.id = id;
        this.text = text;
        this.value = value;
    }

    public void readFromParcel(Parcel source) {
        // 读写顺序必须一致；这是同版本 App 内部协议，不承诺跨版本字段兼容。
        id = source.readInt();
        text = source.readString();
        value = source.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(text);
        dest.writeLong(value);
    }

    @Override
    public int describeContents() { return 0; }

    public static final Creator<IpcRecord> CREATOR = new Creator<IpcRecord>() {
        @Override
        public IpcRecord createFromParcel(Parcel source) {
            IpcRecord record = new IpcRecord();
            record.readFromParcel(source);
            return record;
        }

        @Override
        public IpcRecord[] newArray(int size) { return new IpcRecord[size]; }
    };

    @Override
    public String toString() {
        return "IpcRecord(id=" + id + ", text=" + text + ", value=" + value + ")";
    }
}
