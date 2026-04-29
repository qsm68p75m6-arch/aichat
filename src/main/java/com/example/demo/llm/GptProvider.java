package com.example.demo.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * OpenAI GPT 模型提供者
 * 需要在 application.properties 中配置 openai.api.key 才启用
 */
@Component
@ConditionalOnProperty(name = "openai.api.key")  // 有配置才启用
public class GptProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GptProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private final RestTemplate restTemplate = new RestTemplate();

    private final List<String> supportedModels = List.of(
            "gpt-4o",           // 最新多模态，性价比高 ★推荐
            "gpt-4o-mini",      // 便宜快速版
            "gpt-4-turbo",      // GPT-4 Turbo
            "o1-mini",          // 推理强（数学/代码）
            "o1-preview"        // 推理最强（贵）
    );

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o}")
    private String currentModel;

    @Override
    public List<String> getSupportedModels() { return supportedModels; }

    @Override
    public String getCurrentModel() { return currentModel; }

    @Override
    public void switchModel(String modelName) {
        if (!supportedModels.contains(modelName)) {
            throw new LlmException("OpenAI", modelName, "不支持。可用: " + supportedModels);
        }
        this.currentModel = modelName;
        log.info("[GPT] 已切换到模型: {}", modelName);
    }

    @Override
    public String chat(List<Map<String, String>> messages,
                       Double temperature, Integer maxTokens) {
        Map<String, Object> body = Map.of(
                "model", currentModel,
                "messages", messages,
                "temperature", temperature != null ? temperature : 0.7,
                "max_tokens", maxTokens != null ? maxTokens : 4096
        );

        Map<String, Object> response = restTemplate.postForObject(API_URL, body, Map.class,
                Map.of("Authorization", "Bearer " + apiKey));

        // 解析响应: choices[0].message.content
        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException("OpenAI", currentModel, "API返回空结果");
        }
        Map<?, ?> choice = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return (String) message.get("content");
    }

    @Override
    public void chatStream(List<Map<String, String>> messages,
                           Double temperature, Integer maxTokens,
                           StreamCallback callback) {
        // OpenAI 流式调用需要 SSE 解析
        // 简化实现：先非流式拿到完整结果再分段回调（生产环境应用真正的SSE流）
        String fullResponse = chat(messages, temperature, maxTokens);
        
        // 模拟分块输出（实际应使用 SseEmitter / WebClient 流式解析）
        int chunkSize = 20;
        for (int i = 0; i < fullResponse.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, fullResponse.length());
            boolean isComplete = (end >= fullResponse.length());
            callback.onToken(fullResponse.substring(i, end), isComplete);
        }
    }

    @Override
    public String getProviderName() { return "OpenAI (GPT)"; }

    @Override
    public String getDescription() {
        return "OpenAI GPT系列，全球最知名的大模型，多模态能力强";
    }
}
