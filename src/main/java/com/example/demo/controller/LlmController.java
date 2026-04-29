package com.example.demo.controller;

import com.example.demo.common.Result;
import com.example.demo.dto.ChatRequestDto;
import com.example.demo.llm.LlmRouter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 模型管理与多模型聊天控制器
 */
@Tag(name = "模型管理", description = "多模型切换、查询、统一聊天接口")
@RestController
@RequestMapping("/llm")
public class LlmController {

    @Autowired
    private LlmRouter llmRouter;

    /**
     * 查看所有已注册的模型提供者及其可用模型
     */
    @Operation(summary = "查看可用模型列表")
    @GetMapping("/models")
    public Result getAvailableModels() {
        return Result.ok(llmRouter.getAvailableProviders());
    }

    /**
     * 切换默认模型提供者
     * 例: POST /llm/switch {"provider": "openai", "model": "gpt-4o"}
     */
    @Operation(summary = "切换默认模型")
    @PostMapping("/switch")
    public Result switchModel(@RequestBody Map<String, String> body) {
        String provider = body.get("provider");
        String model = body.get("model");

        // 1. 切换大模型提供者（如 glm → openai）
        if (provider != null && !provider.isEmpty()) {
            llmRouter.setDefaultProvider(provider);
        }

        // 2. 切换子模型（如 gpt-4o → gpt-4o-mini）
        if (model != null && !model.isEmpty()) {
            String targetProvider = (provider != null && !provider.isEmpty())
                    ? provider
                    : llmRouter.getDefaultProviderName();
            llmRouter.switchSubModel(targetProvider, model);
        }

        return Result.ok(Map.of(
                "default_provider", llmRouter.getDefaultProviderName(),
                "current_config", llmRouter.getAvailableProviders(),
                "message", "切换成功"
        ));
    }

    /**
     * 统一聊天接口（指定使用哪个模型）
     * 
     * 请求示例:
     * {
     *   "message": "你好",
     *   "provider": "openai",      // 可选，不传则用默认
     *   "model": "gpt-4o-mini",     // 可选，不传则用该 provider 默认
     *   "temperature": 0.7,         // 可选
     *   "max_tokens": 2048          // 可选
     * }
     */
    @Operation(summary = "多模型统一聊天（非流式）")
    @PostMapping("/chat")
    public Result chat(@RequestBody Map<String, Object> body) {
        String message = (String) body.get("message");
        String provider = (String) body.get("provider");
        String model = (String) body.get("model");
        Double temperature = body.get("temperature") != null ?
                ((Number) body.get("temperature")).doubleValue() : null;
        Integer maxTokens = body.get("max_tokens") != null ?
                ((Number) body.get("max_tokens")).intValue() : null;

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", message)
        );

        String reply = llmRouter.chat(provider, model, messages, temperature, maxTokens);
        return Result.ok(Map.of("reply", reply));
    }

    /**
     * 统一流式聊天接口
     *
     * 请求示例:
     * {
     *   "message": "写一首诗",
     *   "provider": "glm",
     *   "session_id": "xxx"
     * }
     */
    @Operation(summary = "多模型流式对话（SSE）")
    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(60_000L);  // 60秒超时

        String message = (String) body.get("message");
        String provider = (String) body.get("provider");
        String model = (String) body.get("model");

        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", message)
        );

        try {
            llmRouter.chatStream(provider, model, messages,
                    null, null,
                    (token, isComplete) -> {
                        emitter.send(SseEmitter.event()
                                .data(token)
                                .name(isComplete ? "done" : "token"));
                        if (isComplete) {
                            emitter.complete();
                        }
                    });
        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}
