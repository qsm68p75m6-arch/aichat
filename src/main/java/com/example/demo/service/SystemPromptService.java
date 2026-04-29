package com.example.demo.service;

import com.example.demo.entity.SystemPrompt;
import java.util.List;

public interface SystemPromptService {
    SystemPrompt save(SystemPrompt prompt);   // 创建后返回含id的对象
    void update(SystemPrompt prompt);
    void delete(Integer id, Integer currentUserId);  // 删除时校验权限
    SystemPrompt getById(Integer id);
    List<SystemPrompt> getListForUser(Integer userId);   // 用户可见的角色列表
}

