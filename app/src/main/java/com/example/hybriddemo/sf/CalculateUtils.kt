package com.example.hybriddemo.sf

object CalculateUtils {

    /**
     * 查找第一个相等的元素 下标
     */
    fun firstBoundSearch(nums: IntArray, target: Int): Int {
        var left = 0;
        var right = nums.size - 1;
        if (left > right) return -1
        var result = -1
        // [1,2,3,4,5,5,5,6,7,8,9,10], 5
        while (left <= right){
            val mid = left + (right - left) / 2
            if(nums[mid] == target){
                result = mid
                right = mid -1
            } else if(nums[mid] < target){
                left = mid + 1
            } else {
                right = mid - 1
            }
        }

        return result
    }
}