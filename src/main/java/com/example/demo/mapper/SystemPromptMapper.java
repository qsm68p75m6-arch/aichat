package com.example.demo.mapper;

import com.example.demo.entity.SystemPrompt;
import java.util.List;

public interface SystemPromptMapper {
    void save(SystemPrompt prompt);
    void update(SystemPrompt prompt);
    void delete(Integer id);
    SystemPrompt getById(Integer id);
    List<SystemPrompt> getByUserId(Integer userId);   // 系统内置 + 用户自定义
    List<SystemPrompt> getSystemBuiltIn();            // 仅系统内置(userId=0)
}
