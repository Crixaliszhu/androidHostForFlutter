package com.example.hybriddemo.thread

data class PriorityTask(
    val priority: Int,
    val block: () -> Unit,
) : Runnable, Comparable<PriorityTask> {

    override fun run() = block()

    override fun compareTo(other: PriorityTask) = other.priority - priority
}