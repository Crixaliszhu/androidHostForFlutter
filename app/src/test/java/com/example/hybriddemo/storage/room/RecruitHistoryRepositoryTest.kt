package com.example.hybriddemo.storage.room

import com.example.hybriddemo.storage.room.dao.RecruitHistoryDao
import com.example.hybriddemo.storage.room.table.RecruitHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecruitHistoryRepositoryTest {

    @Test
    fun saveHistory_upsertsBusinessEntity() = runTest {
//        val dao = FakeRecruitHistoryDao()
//        val repository = RecruitHistoryRepository(dao)
//
//        repository.saveHistory(id = "history_1", title = "招聘水电工", city = "重庆")
//
//        val entity = dao.items.single()
//        assertEquals("history_1", entity.id)
//        assertEquals("招聘水电工", entity.title)
//        assertEquals("重庆", entity.city)
        val list = intArrayOf(1, 2, 5, 9, 0, 0, 0)
        val list2 = intArrayOf(4, 7, 8)
//        val arr = CalculateMain.twoNumberSum(list, 10)
        val arr = mergeSortArr(list, 4, list2, 3)
        println("答案是 ======================= >")
        for (i in arr) {
            println(i)
        }
    }

    /**
     * 合并有序数组，第一个数组预留足够的空间
     */
    fun mergeSortArr(a: IntArray, m: Int, b: IntArray, n: Int): IntArray {
        var i = m - 1
        var j = n - 1
        var k = m + n - 1
        while (i >= 0 && j >= 0) {
            a[k--] = if (a[i] > b[j]) a[i--] else b[j--]
        }
        while (j >= 0) {
            a[k--] = b[j--]
        }
        return a
    }

    /**
     * 移动0到数组末尾
     * 0, 2, 0, 3, 4, 5
     */
    fun moveZeroToLast(list: IntArray): IntArray {
        var slow = 0;
        // 0,2,0,3,4,5

        // 0  为0，fast+1 = 1
        // 2,2  不为0，slow+1 = 1,fast+1 = 2
        // 2,2, 为0，fast+1 = 3
        // 2,3,0 不为0，slow+1 = 2,fast+1=4
        // 2,3,4 不为0，slow+1 = 3,fast+1=5
        // 2,3,4,5 不为0，slow+1 =4,fast+1=6  退出循环
        // 2,3,4,5,4,5  最后 slow = 4
        for (fast in list.indices) {
            if (list[fast] != 0) {
                list[slow] = list[fast];
                slow++
            }
        }
        println("赋值前是 ======================= >")
        for (i in list) {
            println(i)
        }
        while (slow < list.size) {
            list[slow] = 0
            slow++
        }

        return list
    }

    /**
     * 原地修改该数组，删除重复的元素
     */
    fun deleteRepeats(arr: IntArray): Int {
        if (arr.isEmpty()) return 0
        var slow = 0;
        for (fast in arr.indices) {
            if (arr[slow] != arr[fast]) {
                // 不重复时， 先移动
                slow++
                // 再赋值，跳过重复的值
                arr[slow] = arr[fast]
            }
            // 如果重复了，则slow不动
        }
        return slow + 1
    }

    /**
     * 找出两素之和 = sum 的元素的下标
     */
    fun getTwoSum(arr: IntArray, sum: Int): IntArray {
        val temp = HashMap<Int, Int>()
        for (i in arr.indices) {
            val target = sum - arr[i]
            if (temp.containsKey(target)) {
                return intArrayOf(temp[target] ?: 0, i)
            }
            temp[arr[i]] = i
        }

        return intArrayOf(0, 0)
    }

    @Test
    fun readSummary_returnsReadableBusinessText() = runTest {
//        val dao = FakeRecruitHistoryDao()
//        val repository = RecruitHistoryRepository(dao)
//        repository.saveHistory(id = "history_2", title = "招聘泥瓦工", city = "成都")
//
//        assertEquals("history_2 / 招聘泥瓦工 / 成都", repository.readSummary())
        val f9 = IntListNode(19, null)
        val f8 = IntListNode(18, f9)
        val f7 = IntListNode(17, f8)
        val f6 = IntListNode(16, f7)
        val f5 = IntListNode(15, f6)
        val f4 = IntListNode(14, f5)
        val f3 = IntListNode(13, f4)
        val f2 = IntListNode(12, f3)
        val f1 = IntListNode(11, f2)
        val fHead = IntListNode(10, f1)

        val s3 = IntListNode(9, null)
        val s2 = IntListNode(8, s3)
        val s1 = IntListNode(3, s2)
        val sHead = IntListNode(1, s1)


        // [2,5,6,7], [1,3,8,9]
//        val result = CalculateMain.mergeTwoLink(fHead, sHead)
        val result = deleteReverseN(fHead, 3)
        var iter = result
        println("打印答案 ==================================")
        while (iter.next != null) {
            println("元素：${iter.node}")
            iter = iter.next;
        }
        println("元素：${iter.node}")
    }

    /**
     * 合并有序链表：
     * 双指针
     */
    fun mergeSortLink(link1: IntListNode, link2: IntListNode): IntListNode {
        if (link1.node <= link2.node) {
            if (link1.next == null) return link2
            link1.next = mergeSortLink(link1.next, link2)
            return link1
        } else {
            if (link2.next == null) return link1
            link2.next = mergeSortLink(link1, link2.next)
            return link2
        }
    }

    /**
     * 删除倒数第N个节点
     */
    fun deleteReverseN(link: IntListNode, n: Int): IntListNode {
        var fast: IntListNode? = link
        var slow = link
        repeat(n + 1) {
            fast = fast?.next
        }
        println("fast1 = ${fast?.node}")
        while (fast != null) {
            fast = fast?.next
            slow = slow.next
        }
        println("fast2 = ${fast?.node}")
        println("slow1 = ${slow.node}")
        slow.next = slow.next.next
        println("slow2 = ${slow.node}")
        return link
    }

    private class FakeRecruitHistoryDao : RecruitHistoryDao {
        val items = mutableListOf<RecruitHistoryEntity>()

        override fun observeAll(): Flow<List<RecruitHistoryEntity>> = flowOf(items)

        override suspend fun queryAll(): List<RecruitHistoryEntity> {
            return items.sortedByDescending { it.updatedAt }
        }

        override suspend fun upsert(entity: RecruitHistoryEntity) {
            items.removeAll { it.id == entity.id }
            items.add(entity)
        }

        override suspend fun deleteById(id: String) {
            items.removeAll { it.id == id }
        }

        override suspend fun clear() {
            items.clear()
        }
    }
}
