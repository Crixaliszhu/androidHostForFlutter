package com.example.hybriddemo.storage.room;

import java.util.HashMap;

public class CalculateMain {

    /**
     * 两数之和: 找出数组中两数相加 = sum 的这两个数，返回其下标
     * 推荐算法：哈希表，一遍遍历，一遍将数组中的 元素，元素下标存入 hashMap中；
     * 哈希表查询O1所以遍历后面的数组元素时从哈希表看i查找是否有满足要求的元素有则直接返回；
     * 没有就存入哈希表
     *
     * @param arr 目标数组
     * @param sum 和
     */
    public static int[] twoNumberSum(int[] arr, int sum) {
        HashMap<Integer, Integer> temp = new HashMap<>();
        for (int i = 0; i < arr.length; i++) {
            // 本次要找的目标值
            int another = sum - arr[i];
            if (temp.containsKey(another)) {
                return new int[]{temp.get(another), i};
            }
            temp.put(arr[i], i);
        }

        return new int[0];
    }

    /**
     * 移除重复元素：要求原地修改数组，并返回去重后的长度
     * 原地修改该数据，考虑快慢指针
     */
    public static int deleteRepeatNum(int[] arr) {
        int slow = 0;
        // 1, 2, 2, 3, 4, 4, 5

        // 1, 2,
        // 1, 2,  arr[1] == arr[2] 慢指针不动
        // 1, 2, 3 arr[1] != arr[3]  将1++ arr[2] = 3,中间重复的2被跳过了
        // 1, 2, 3, 4
        // 1, 2, 3, 4
        // 1, 2, 3, 4, 5
        for (int fast = 1; fast < arr.length; fast++) {
            if (arr[fast] != arr[slow]) {
                slow++;
                arr[slow] = arr[fast];
            }
        }
        return slow + 1;
    }

    /**
     * 将所有0移动到数组末尾
     *
     * @param arr
     * @return
     */
    public static int[] moveZeroToLast(int[] arr) {
        int slow = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] != 0) {
                int temp = arr[slow];
                arr[slow] = arr[i];
                arr[i] = temp;
                slow++;
            }
        }
        return arr;
    }


    /**
     * 合并两个有序数组：正向双指针
     * p1 指向数组1的最后一个元素，p2指向数组2的最后一个元素，从后往前遍历；p指向 m+n-1
     * arr[p] = max(p1,p2),较大这放到数组1最后一个；
     *
     * @param arr
     * @param brr
     * @return
     */
    public static int[] mergeSortArray(int[] arr, int[] brr) {
        int m = arr.length;
        int n = brr.length;
        int p1 = m - 1;
        int p2 = n - 1;
        int[] nrr = new int[m + n];
        int p = m + n - 1;
        while (p1 >= 0 && p2 >= 0) {
            if (arr[p1] >= brr[p2]) {
                nrr[p] = arr[p1];
                p1--;
            } else {
                nrr[p] = brr[p2];
                p2--;
            }
            p--;
        }
        while (p1 >= 0) {
            nrr[p] = arr[p1];
            p1--;
            p--;
        }
        while (p2 >= 0) {
            nrr[p] = arr[p2];
            p2--;
            p--;
        }
        return nrr;
    }

    /**
     * 合并两个有序数组：第一个数组尾部预留空间：逆向双指针
     * p1 指向数组1的最后一个元素，p2指向数组2的最后一个元素，从后往前遍历；p指向 m+n-1
     * arr[p] = max(p1,p2),较大这放到数组1最后一个；
     *
     * @param arr
     * @param brr
     * @return
     */
    public static int[] mergeSortArrayReverse(int[] arr, int m, int[] brr, int n) {
        int p1 = m - 1;
        int p2 = n - 1;
        int p = m + n - 1;
//        while (p1 >= 0 && p2 >= 0) {
//            if (arr[p1] > brr[p2]) {
//                arr[p] = arr[p1];
//                p1--;
//            } else {
//                arr[p] = brr[p2];
//                p2--;
//            }
//            p--;
//        }
//        while (p2 >= 0) {
//            arr[p] = brr[p2];
//            p2--;
//            p--;
//        }
        while (p2 >= 0) {
            // p1先遍历完，则还需要把p2放入p
            if (p1 >= 0 && arr[p1] > brr[p2]) {
                arr[p] = arr[p1];
                p1--;
            } else {
                arr[p] = brr[p2];
                p2--;
            }
            p--;
        }

        return arr;
    }

    /**
     * 给定你一个数组：元素有正有负，找出最大字数住和是多少？子数组最少一个元素
     * [-2,1,-3,4,-1,2,1,-5,4]
     * 最大子数组和为 6：[4,-1,2,1]
     * @param nums
     * @return
     */
    public static int getMaxChildArrSum(int[] nums){
        int current = nums[0];
        int max = nums[0];

        for (int i = 1; i < nums.length; i++) {
            // 如果从i位置新开子数组能得到更大的值，则新开，否则就把它加到子数组里计算和；
            current = Math.max(nums[i], current + nums[i]);
            // 记录当前最大的子数组和
            max = Math.max(max, current);
        }

        return max;
    }

    public static int[] mergeShort(int[] nums1, int m, int[] nums2, int n) {
        int k = m + n - 1;
        int i = m - 1;
        int j = n - 1;
        while (i >= 0 && j >= 0) {
            nums1[k--] = (nums1[i] > nums2[j]) ? nums1[i--] : nums2[j--];
        }
        while (j >= 0) {
            nums1[k--] = nums2[j--];
        }
        return nums1;
    }

    /**
     * 合并有序链表
     *
     * @param p1 [1,3,5,7,9]
     * @param p2 [  2,4,6]
     * @return
     */
    public static IntListNode mergeTwoLink(IntListNode p1, IntListNode p2) {
        IntListNode result = new IntListNode(0, null);
        IntListNode head = result;
        while (p1 != null && p2 != null) {
            if (p1.node < p2.node) {
                result.next = p1;
                p1 = p1.next;
            } else {
                result.next = p2;
                p2 = p2.next;
            }
            result = result.next;
        }
        result.next = (p1 != null) ? p1 : p2;
        return head.next;
    }

    /**
     * 获取链表中间节点：快慢指针，让快指针走完时，慢指针刚好停留在中间节点位置；
     *
     * @param head
     * @return
     */
    public static IntListNode getLinkMediumNode(IntListNode head) {
        IntListNode fast = head;
        IntListNode slow = head;
        while (fast != null && fast.next != null && slow != null) {
            slow = slow.next;
            fast = fast.next.next;
        }

        return slow;
    }

    /**
     * 判断链表是否有环
     * 分析：
     * 如果链表没有环[1,2,3,5,null]，则尾指针后为null，循环时判断循环指针 == null则会退出循环；
     * 如果链表有环[1,2,3,5,1]，则尾指针会指向头指针，循环退出条件为 找到相等的节点；
     * <p>
     * 需要两个指针，两个指针速度肯定不能一样，否则就会直接退出； 快慢指针；
     *
     * @return
     */
    public static Boolean isLinkLoop(IntListNode head) {
        IntListNode fast = head;
        IntListNode slow = head;

        // 因为快指针跑得快，先指向null的概率大，能够更加早的退出循环；
        while (fast != null && fast.next != null) {
            fast = fast.next.next;
            slow = slow.next;
            if (slow == fast) {
                return true;
            }
        }

        return false;
    }

}
