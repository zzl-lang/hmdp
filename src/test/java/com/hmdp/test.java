package com.hmdp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.Callable;

public class test {
    // 定义一个字符型常量
    public static final char LETTER_A = 'A';

    // 定义一个字符串常量
    public static final String GREETING_MESSAGE = "Hello, world!";
    private int a = 10;

    String[] map = {
            " ","","abc","def","ghi","jkl","mno","pqrs","tuv","wxyz"
    };
    public static void main(String[] args) {
        List<Integer> strings = Arrays.asList(1, 2, 3);
        Collections.sort(strings, new Comparator<Integer>() {
            @Override
            public int compare(Integer o1, Integer o2) {
                return o2 - o1;
            }
        });
        Collections.sort(strings,(Integer o1,Integer o2) -> o1 - o2);
        System.out.println(strings);
        Runnable printEven = new Thread(() ->{

        });





    }

}
