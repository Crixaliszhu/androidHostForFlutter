package com.example.hybriddemo.storage.room;

public interface IJavaFactory {
    String NAME = "name";

    default String getName(){
        return NAME;
    }

    int getValue();
}
