package com.example.demo.entity;

import lombok.Data;
import java.util.Date;

@Data
public class SystemPrompt {
    private Integer id;
    private String name;           // 角色名称
    private String systemPrompt;   // 系统提示词（核心字段）
    private String avatar;         // 头像URL
    private String description;    // 角色描述
    private Integer userId;        // 0=系统内置, >0=用户自定义
    private Date createTime;
}
