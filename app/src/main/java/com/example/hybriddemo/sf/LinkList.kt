package com.example.hybriddemo.sf

class IntListNode(
    var node: Int,
    var next: IntListNode? = null,
)

object LinkList {

    /**
     * 使用两个变量分别存储：cur, pre,顺序遍历list，反转每个元素的next
     */
    fun reserve(head: IntListNode): IntListNode? {
        var current: IntListNode? = head
        var pre: IntListNode? = null
        while (current != null) {
            val next = current.next
            current.next = pre
            pre = current
            current = next
        }
        return pre
    }

    /**
     * 合并有序链表
     * 一个虚拟头指针，两个list指针
     * 每轮对比头指针的next指向 两个指针较小的那个；将头指针指向新加入的小的那个
     */
    fun mergeSortList(list1: IntListNode, list2: IntListNode): IntListNode? {
        // 虚拟头
        var head = IntListNode(0)
        var first: IntListNode? = list1
        var second: IntListNode? = list2
        while (first != null && second != null) {
            if (first.node <= second.node) {
                head.next = first
                first = first.next
            } else {
                head.next = second
                second = second.next
            }
        }
        head.next = first ?: second
        return head.next
    }
}