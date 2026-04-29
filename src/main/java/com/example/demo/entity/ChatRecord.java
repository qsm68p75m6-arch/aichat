package com.example.demo.entity;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.Date;
@Data
public class ChatRecord {
    private Integer id;
    private Integer userId;
    private String question;
    private String answer;
    private Date createTime;
    private String sessionId;
}


