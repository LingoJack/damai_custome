package com.example.controller;

import com.example.common.Result;
import com.example.dto.JobCallBackDto;
import com.example.service.JobRunRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * @program: toolkit
 * @description:
 * @author: k
 * @create: 2023-06-28
 **/
@RestController
@RequestMapping("/jobRunRecord")
public class JobRunRecordController {
    
    @Autowired
    private JobRunRecordService jobRunRecordService;
    
    @RequestMapping(value = "/callBack",method = RequestMethod.POST)
    public Result<Integer> callBack(@Valid @RequestBody JobCallBackDto JobCallBackDto) {
        return Result.success(jobRunRecordService.callBack(JobCallBackDto));
    }
}