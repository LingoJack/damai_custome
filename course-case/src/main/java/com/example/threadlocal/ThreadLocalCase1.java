package com.example.threadlocal;

import java.util.Random;

/**
 * @program: toolkit
 * @description:
 * @author: lk
 * @create: 2023-04-19
 **/
public class ThreadLocalCase1 {
    
    private static ThreadLocal<Integer> threadLocal = new ThreadLocal<>();
    
    public static void main(String[] args) {
        Random random = new Random(); 
        for (int i = 0; i < 5; i++) {
            Thread thread = new Thread(() -> {
                int value = random.nextInt(10000);
                System.out.println(Thread.currentThread().getName() + "开始执行，放入值，值为 : " + value);
                threadLocal.set(value); 
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println(Thread.currentThread().getName() + "结束执行，进行取值，值为 : " + threadLocal.get());
            }); 
            thread.setName("thread - " + i); 
            thread.start();
        }
    }
}