package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.ChatRequestDto;
import com.example.demo.entity.ChatRecord;
import com.example.demo.entity.SystemPrompt;
import com.example.demo.service.ChatRecordService;
import com.example.demo.service.SystemPromptService;
import com.example.demo.util.AiUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
/**
 * AI 聊天控制器
 * 提供流式对话、历史记录查询、会话列表等功能
 */
@Tag(name="AI聊天",description = "AI流式对话与历史记录相关接口")//新增：分组标签
@RestController
@RequestMapping("/ai")
public class AiController {
    @Autowired
    private ChatRecordService chatRecordService;
    @Autowired
    private SystemPromptService systemPromptService;
    private  static final Logger log= LoggerFactory.getLogger(AiController.class);

    /**
     * 流式聊天（支持session隔离）
     * - 若传sessionid：基于该session的历史记录作为上下文
     * - 若不传sessionid：生成新sessionid，无历史上下文
     */
    @Operation(summary = "流式对话",
            description = "发送信息给AI，返回SSE流式相应")
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody ChatRequestDto request, HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(0L);
        Integer userId = (Integer) httpRequest.getAttribute("userId");
        try {
            doChatStreamAsync(request, userId, emitter);
        } catch (Exception e) {
            log.error("[AI] 启动异步聊天任务失败", e);   // ✅ 新增：启动阶段异常日志
            emitter.completeWithError(e);
        }

        return emitter;
    }
    @Async("aiTaskExecutor")
    // ✅ 关键：指定使用 aiTaskExecutor 这个线程池
    public void doChatStreamAsync(ChatRequestDto request, Integer userId, SseEmitter emitter) {
        try {
            String question = request.getQuestion();
            String sessionId = request.getSessionId();
            Integer promptId = request.getPromptId();

            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = UUID.randomUUID().toString().replace("-", "");
            }

            log.info("[AI] 用户{} 开始流式对话, sessionId={}", userId, sessionId);

            List<ChatRecord> history = chatRecordService.getBySessionId(userId, 20, sessionId);

            List<Map<String, String>> messages = new ArrayList<>();
            Collections.reverse(history);

            for (ChatRecord record : history) {
                Map<String, String> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", record.getQuestion());

                Map<String, String> aiMsg = new HashMap<>();
                aiMsg.put("role", "assistant");
                aiMsg.put("content", record.getAnswer());

                messages.add(userMsg);
                messages.add(aiMsg);
            }

            Map<String, String> current = new HashMap<>();
            current.put("role", "user");
            current.put("content", question);
            messages.add(current);

            // 查询角色设定
            String systemPromptText = null;
            if (promptId != null && promptId > 0) {
                SystemPrompt sp = systemPromptService.getById(promptId);
                if (sp != null) {
                    systemPromptText = sp.getSystemPrompt();
                }
            }

            String fullAnswer;
            if (systemPromptText != null) {
                fullAnswer = AiUtil.chatStreamWithSystemPrompt(systemPromptText, messages, emitter);
            } else {
                fullAnswer = AiUtil.chatStreamWithContext(messages, emitter);
            }

            // 保存聊天记录
            ChatRecord record = new ChatRecord();
            record.setUserId(userId);
            record.setQuestion(question);
            record.setAnswer(fullAnswer);
            record.setCreateTime(new Date());
            record.setSessionId(sessionId);
            chatRecordService.save(record);

            log.info("[AI] 用户{} 对话完成, sessionId={}, answerLength={}",
                    userId, sessionId, fullAnswer.length());   // ✅ 新增：完成日志

        } catch (Exception e) {
            log.error("[AI] 流式对话异常, userId={}", userId, e);  // ✅ 原来：静默吞异常
            emitter.completeWithError(e);
        }
    }

    /**
     * 获取指定session的聊天历史
     */
    @Operation(summary = "查询历史记录",
            description = "根据会话ID分页查询聊天记录")
    @GetMapping("/history/{sessionId}")
    public Result getSessionHistory(@PathVariable String sessionId, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail("用户未登录或token无效");
        }
        List<ChatRecord> list = chatRecordService.getBySessionId(userId, 100, sessionId);
        return Result.ok(list);
    }

    /**
     * 获取用户的所有session列表（每个session返回最新一条消息）
     */
    @Operation(summary = "查询会话列表",
        description = "获取当前用户所有的会话列表")
    @GetMapping("/sessions")
    public Result getUserSessions(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail("用户未登录或token无效");
        }
        List<ChatRecord> allRecords = chatRecordService.getByUserId(userId);

        Map<String, ChatRecord> sessionMap = new LinkedHashMap<>();
        for (ChatRecord record : allRecords) {
            String sid = record.getSessionId();
            if (sid != null && !sessionMap.containsKey(sid)) {
                sessionMap.put(sid, record);
            }
        }

        return Result.ok(new ArrayList<>(sessionMap.values()));
    }
}