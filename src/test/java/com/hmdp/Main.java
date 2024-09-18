package com.hmdp;

public class Main {
    public static void main(String[] args) {
        Thread t1 = new MyThread("小明");
        Thread t2 = new MyThread("小李");
        t1.start();
        t2.start();
        Runnable runnable = new RunnableThread();
        Thread t3 = new Thread(runnable,"小白");
        t3.start();

        for (int i = 0; i < 3 ; i++){
            System.out.println(Thread.currentThread().getName() + i);
        }

    }
}
