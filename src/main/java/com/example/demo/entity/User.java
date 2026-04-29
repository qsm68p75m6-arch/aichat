//实体类
//对应数据库表
package com.example.demo.entity;
import lombok.Data;
@Data
public class User {
    private Integer id;
    private String username;
    private String password;

}
