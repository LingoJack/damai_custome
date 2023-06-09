package com.example.lambda;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: toolkit
 * @description:
 * @author: k
 * @create: 2023-06-09
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Integer id;
    
    private String name;
}
