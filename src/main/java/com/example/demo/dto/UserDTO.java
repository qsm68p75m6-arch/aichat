package com.example.demo.dto;

import java.util.Map;
import java.util.Objects;
//隐藏关键信息
public class UserDTO {
    private Integer id;
    private String username;

    // Getter & Setter（Jackson 序列化需要）
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}