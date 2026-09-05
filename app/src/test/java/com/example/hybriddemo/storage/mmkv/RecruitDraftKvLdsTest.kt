package com.example.hybriddemo.storage.mmkv

import com.example.hybriddemo.storage.room.IntListNode
import org.junit.Test

class RecruitDraftKvLdsTest {

    @Test
    fun saveLastDraft_writesOnlyBusinessKeys() {
//        val store = FakeKvStore()
//        val lds = RecruitDraftKvLds(store)
//
//        lds.saveLastDraft(id = "draft_1", title = "招聘焊工")
//
//        assertEquals("draft_1", store.values[RecruitDraftKvKeys.LAST_DRAFT_ID])
//        assertEquals("招聘焊工", store.values[RecruitDraftKvKeys.LAST_DRAFT_TITLE])
//        assertEquals("draft_1 / 招聘焊工", lds.readLastDraft())
        val node4 = IntListNode(4, null)
        val node3 = IntListNode(3, node4)
        val node2 = IntListNode(2, node3)
        val node1 = IntListNode(1, node2)
        val head = IntListNode(0, node1)
        node4.next = node1
        // 0-1-2-3-4-1
        val has = findCircleLinkEntry(head)
        println("结果是：${has?.node}")
    }

    @Test
    fun clearLastDraft_removesBusinessKeys() {
//        val store = FakeKvStore()
//        val lds = RecruitDraftKvLds(store)
//        lds.saveLastDraft(id = "draft_2", title = "招聘木工")
//
//        lds.clearLastDraft()
//
//        assertEquals("none / empty", lds.readLastDraft())
        val arr = intArrayOf(1, 2, 3, 4, 5, 6, 7)
        turnRight(arr, 3)
        println("打印答案 ================================== ${arr.toList()}")
    }


    /**
     * 查找第一个相等的元素 下标；一直向左找
     */
    fun firstBoundSearch(nums: IntArray, target: Int): Int {
//        var left = 0;
//        var right = nums.size - 1;
//        if (left > right) return -1
//        var result = -1
//        // [1,2,3,4,5,5,5,6,7,8,9,10], 5
//        while (left <= right) {
//            val mid = left + (right - left) / 2
//            if (nums[mid] == target) {
//                result = mid
//                right = mid - 1 // 继续向左搜索
//            } else if (nums[mid] < target) {
//                left = mid + 1
//            } else {
//                right = mid - 1
//            }
//        }
//        return result

        var left = 0
        var right = nums.size
        while (left < right) {
            val mid = left + (right - left) / 2
//            // [1,2,3,4,5,5,6,6,7,8,9], 5
            if (nums[mid] >= target) {
                right = mid
            } else {
                left = mid + 1
            }
//            if (nums[mid] <= target) {
//                left = mid // 移动左边界，但包含mid, 一直向右找
//            } else {
//                right = mid - 1
//            }
        }

        return left
    }

    /**
     * 最后一个等于目标值的元素: 一直向右找
     */
    fun getLastBound(nums: IntArray, target: Int): Int {
        var left = 0;
        var right = nums.size
        while (left < right) {
            val mid = left + (right - left) / 2
            if (nums[mid] <= target) {
                left = mid // 移动左边界，但包含mid, 一直向右找
            } else {
                right = mid - 1
            }
        }
        return left
    }


    /**
     * 寻找最后一个匹配的目标元素下标
     * [1,2,3,4,5,5,5,6,7,8,9,10], 5
     */
    fun firstRightBound(nums: IntArray, target: Int): Int {
        var left = 0
        var right = nums.size - 1
        var result = -1
        while (left <= right) {
            val mid = left + (right - left) / 2
            if (nums[mid] == target) {
                result = mid
                left = mid + 1 // 继续向右搜索
            } else if (nums[mid] < target) {
                left = mid + 1
            } else {
                right = mid - 1
            }
        }

        return result
    }

    /**
     * 将数组原地向右旋转k
     */
    fun turnRight(nums: IntArray, k: Int) {
        if (nums.isEmpty()) return
        val realK = k % nums.size
        reserveArray(nums, 0, nums.lastIndex)
        println("打印 nums 1 ================================== ${nums.toList()}")
        reserveArray(nums, 0, realK - 1)
        println("打印 nums 2 ================================== ${nums.toList()}")

        reserveArray(nums, realK, nums.lastIndex)

    }

    /**
     * 反转指定子数组：两两调换位置
     */
    fun reserveArray(nums: IntArray, start: Int, end: Int) {
        var left = start
        var right = end
        while (left < right) {
            val temp = nums[left]
            nums[left] = nums[right]
            nums[right] = temp

            left++
            right--
        }
    }

    /**
     * 判断链表是否有环
     * 快慢指针：慢指针走一步，快指针走两步，如果链表无环，则快指针最终会指向null；
     * 如果链表有环，则快慢指针会相遇；
     */
    fun checkLinkHasCircle(head: IntListNode): Boolean {
        var fast = head
        var slow = head
        while (fast != null && fast.next != null) {
            fast = fast.next.next
            slow = slow.next
            if (fast == slow) { // 相遇则说明有环
                return true
            }
        }
        return false
    }

    /**
     * 找出环形链表的入口
     */
    fun findCircleLinkEntry(head: IntListNode?): IntListNode? {
        var fast: IntListNode? = head
        var slow: IntListNode? = head
        while (fast?.next != null) {
            fast = fast.next?.next
            slow = slow?.next
            if (slow === fast) { // 第一次相遇
                var p = head
                while (p !== slow) {
                    p = p?.next
                    slow = slow?.next
                }
                return p
            }
        }
        return null
    }
}
