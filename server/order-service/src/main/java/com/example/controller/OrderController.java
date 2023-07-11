package com.example.controller;

import com.example.common.Result;
import com.example.dto.GetOrderDto;
import com.example.dto.InsertOrderDto;
import com.example.dto.PayOrderDto;
import com.example.service.OrderService;
import com.example.vo.GetOrderVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * @program: toolkit
 * @description:
 * @author: k
 * @create: 2023-04-17
 **/
@RestController
@RequestMapping("/order")
public class OrderController {
    
    @Autowired
    private OrderService orderService;
    
    @PostMapping(value = "/getOrder")
    public GetOrderVo getOrder(@RequestBody GetOrderDto getOrderDto){
        return orderService.getOrder(getOrderDto);
    }
    
    @PostMapping(value = "/getOrderV2")
    public GetOrderVo getOrderV2(@RequestBody GetOrderDto getOrderDto){
        return orderService.getOrderV2(getOrderDto);
    }
    
    @PostMapping(value = "/insertOrder")
    public Result<Boolean> insertOrder(@Valid @RequestBody InsertOrderDto dto) {
        return orderService.insert(dto);
    }
    
    @PostMapping(value = "/payOrder")
    public Result<Boolean> payOrder(@Valid @RequestBody PayOrderDto dto) {
        return orderService.pay(dto);
    }
}