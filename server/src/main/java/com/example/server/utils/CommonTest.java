package com.example.server.utils;

import java.util.*;

/**
 * @author yaokm
 * @date 2021/3/3 11:16
 */
public class CommonTest {

    public static int partition(int[] a, int low, int high) {
        int tmp = a[low];
        while (low < high) {
            while (low < high && a[high] > tmp) high--;
            a[low] = a[high];
            while (low < high && a[low] < tmp) low++;
            a[high] = a[low];
        }
        a[low] = tmp;
        return low;
    }

    public static void quick_sort(int[] a, int low, int high) {
        if (low < high) {
            int index = partition(a, low, high);
            quick_sort(a, low, index - 1);
            quick_sort(a, index + 1, high);
        }
    }

    public static void main(String[] args) {
        int[] ints = {1, 2, 5, 90, 34, 23, 65};
        System.out.println("排序前：" + Arrays.toString(ints));
        quick_sort(ints, 0, ints.length - 1);
        System.out.println("排序后" + Arrays.toString(ints));


        HashMap<String, Integer> hashMap = new HashMap<>();
        hashMap.put("a", 1);
        hashMap.put("b", 2);
        System.out.println(hashMap.get("a"));
        System.out.println(hashMap);
        for (String key : hashMap.keySet()) {
            System.out.println(key + hashMap.get(key));
        }
        for (Map.Entry entry : hashMap.entrySet()) {
            System.out.println(entry.getKey() + ";" + entry.getValue());
        }
        hashMap.forEach((key, value) -> System.out.println(key + value));

        Set<Map.Entry<String, Integer>> entrySet = hashMap.entrySet();
        for (Map.Entry<String, Integer> entry : entrySet) {
            System.out.println(entry.toString());
        }


    }
}
