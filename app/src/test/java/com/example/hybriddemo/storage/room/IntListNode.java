package com.example.hybriddemo.storage.room;

/**
 * int 链表结构
 */
public class IntListNode {
    public IntListNode(int node, IntListNode next) {
        this.node = node;
        this.next = next;
    }

    public IntListNode next = null;
    public int node;
}
