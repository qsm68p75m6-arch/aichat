package com.example.demo.dto;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Data
public class ChatRequestDto {
    private String question;
    private String sessionId;
    private Integer promptId;
    // 参数校验

}
