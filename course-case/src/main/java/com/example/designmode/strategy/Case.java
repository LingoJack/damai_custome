package com.example.designmode.strategy;

/**
 * @program: toolkit
 * @description:
 * @author: k
 * @create: 2023-06-09
 **/
public class Case {
    
    public static void main(String[] args) {
        FruitStrategy appleStrategy = FruitStrategyContext.getFruitStrategy("苹果");
        FruitStrategy bananaStrategy = FruitStrategyContext.getFruitStrategy("香蕉");
        appleStrategy.eat();
        bananaStrategy.eat();
    }
}