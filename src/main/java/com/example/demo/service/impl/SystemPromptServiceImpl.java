package com.example.demo.service.impl;

import com.example.demo.mapper.SystemPromptMapper;
import com.example.demo.entity.SystemPrompt;
import com.example.demo.service.SystemPromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class SystemPromptServiceImpl implements SystemPromptService {

    @Autowired
    private SystemPromptMapper systemPromptMapper;
    /**
     * ✅ 新增 @Transactional：save 先 insert 再查询，两步操作需事务原子性
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemPrompt save(SystemPrompt prompt) {
        prompt.setCreateTime(new Date());
        systemPromptMapper.save(prompt);
        // 返回最新插入的记录（按name+createTime查）
        List<SystemPrompt> list = systemPromptMapper.getByUserId(prompt.getUserId());
        return list.stream().filter(p -> p.getName().equals(prompt.getName()))
                .findFirst().orElse(prompt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SystemPrompt prompt) {
        systemPromptMapper.update(prompt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id, Integer currentUserId) {
        SystemPrompt prompt = systemPromptMapper.getById(id);
        if (prompt == null) throw new RuntimeException("角色不存在");
        // 只有创建者本人或系统内置才能删（系统内置建议不让删，这里简化处理）
        if (!prompt.getUserId().equals(currentUserId)) {
            throw new RuntimeException("无权删除此角色");
        }
        systemPromptMapper.delete(id);
    }

    @Override
    public SystemPrompt getById(Integer id) {
        return systemPromptMapper.getById(id);
    }

    @Override
    public List<SystemPrompt> getListForUser(Integer userId) {
        return systemPromptMapper.getByUserId(userId);
    }
}
