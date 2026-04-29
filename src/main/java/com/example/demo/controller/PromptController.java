package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.entity.SystemPrompt;
import com.example.demo.service.SystemPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/prompt")
public class PromptController {

    @Autowired
    private SystemPromptService systemPromptService;

    /**
     * 获取当前用户可用的所有角色列表（系统内置 + 自己创建的）
     */
    @Tag(name = "角色设定",description = "系统提示词/角色人设管理接口")
    @GetMapping("/list")
    public Result list(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) return Result.fail("未登录");
        List<SystemPrompt> list = systemPromptService.getListForUser(userId);
        return Result.ok(list);
    }

    /**
     * 获取单个角色详情
     */
    @Operation(summary = "获取角色详情")
    @GetMapping("/{id}")
    public Result detail(@PathVariable Integer id) {
        SystemPrompt prompt = systemPromptService.getById(id);
        if (prompt == null) return Result.fail("角色不存在");
        return Result.ok(prompt);
    }

    /**
     * 创建自定义角色
     */
    @Operation(summary = "创建自定义角色")
    @PostMapping("/create")
    public Result create(@RequestBody SystemPrompt prompt, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) return Result.fail("未登录");

        prompt.setUserId(userId);  // 标记为该用户的自定义角色
        SystemPrompt saved = systemPromptService.save(prompt);
        return Result.ok(saved);
    }

    /**
     * 更新角色
     */
    @Operation(summary = "更新角色")
    @PutMapping("/update")
    public Result update(@RequestBody SystemPrompt prompt, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) return Result.fail("未登录");
        systemPromptService.update(prompt);
        return Result.ok(null);
    }

    /**
     * 删除角色（只能删除自己创建的）
     */
    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) return Result.fail("未登录");
        try {
            systemPromptService.delete(id, userId);
            return Result.ok(null);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }
}
