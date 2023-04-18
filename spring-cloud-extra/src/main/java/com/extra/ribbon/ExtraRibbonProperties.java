package com.extra.ribbon;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @program: toolkit
 * @description:
 * @author: lk
 * @create: 2023-04-17
 **/
@ConfigurationProperties("spring.cloud.nacos.discovery.metadata")
@Setter
@Getter
public class ExtraRibbonProperties {
    
    private String mark;
}