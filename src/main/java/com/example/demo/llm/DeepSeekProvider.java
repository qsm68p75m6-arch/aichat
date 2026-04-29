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
 * DeepSeek 模型提供者
 * API 格式兼容 OpenAI（可以直接用 OpenAI SDK 调用）
 * 特点：极便宜 + 开源 + MoE 架构
 */
@Component
@ConditionalOnProperty(name = "deepseek.api.key")
public class DeepSeekProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekProvider.class);
    // DeepSeek API 兼容 OpenAI 格式
    private static final String API_URL = "https://api.deepseek.com/chat/completions";

    private final RestTemplate restTemplate = new RestTemplate();

    private final List<String> supportedModels = List.of(
            "deepseek-chat",      // DeepSeek-V3（通用对话）★推荐
            "deepseek-reasoner"   // DeepSeek-R1（强推理，类似 o1）
    );

    @Value("${deepseek.api.key:}")
    private String apiKey;

    @Value("${deepseek.model:deepseek-chat}")
    private String currentModel;

    @Override
    public List<String> getSupportedModels() { return supportedModels; }

    @Override
    public String getCurrentModel() { return currentModel; }

    @Override
    public void switchModel(String modelName) {
        if (!supportedModels.contains(modelName)) {
            throw new LlmException("DeepSeek", modelName, "不支持。可用: " + supportedModels);
        }
        this.currentModel = modelName;
        log.info("[DeepSeek] 已切换到模型: {}", modelName);
    }

    /**
     * DeepSeek API 完全兼容 OpenAI 格式！
     * 所以请求/响应解析逻辑与 GptProvider 相同
     */
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

        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException("DeepSeek", currentModel, "API返回空结果");
        }
        Map<?, ?> choice = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return (String) message.get("content");
    }

    @Override
    public void chatStream(List<Map<String, String>> messages,
                           Double temperature, Integer maxTokens,
                           StreamCallback callback) {
        // 同样兼容 OpenAI SSE 流式格式
        String fullResponse = chat(messages, temperature, maxTokens);
        int chunkSize = 20;
        for (int i = 0; i < fullResponse.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, fullResponse.length());
            callback.onToken(fullResponse.substring(i, end), end >= fullResponse.length());
        }
    }

    @Override
    public String getProviderName() { return "DeepSeek"; }

    @Override
    public String getDescription() {
        return "深度求索大模型，性价比极高（约GPT的1/30价格），开源可部署";
    }
}
