package com.example.dto;

import lombok.Data;

import java.util.Date;

/**
 * @program: toolkit
 * @description:
 * @author: 星哥
 * @create: 2023-06-29
 **/
@Data
public class ApiDataDto {
    
    private String id;
    
    private String headVersion;
    
    private String apiAddress;
    
    private String apiMethod;
    
    private String apiBody;
    
    private String apiParams;
    
    private String apiUrl;
    
    private Date createTime;
    
    private Integer status;
    
    private String callDayTime;
    
    private String callHourTime;
    
    private String callMinuteTime;
    
    private String callSecondTime;
    
    private Integer type;
    
}
