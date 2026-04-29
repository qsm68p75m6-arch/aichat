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
 * Anthropic Claude 模型提供者
 * 需要配置 anthropic.api.key 才启用
 *
 * Claude API 与 OpenAI 格式不同：
 * - URL 不同
 * - 请求/响应格式不同
 * - 使用 x-api-key 头而非 Bearer Token
 * - 参数名不同（max_tokens 是必填的！）
 */
@Component
@ConditionalOnProperty(name = "anthropic.api.key")
public class ClaudeProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProvider.class);
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    private final RestTemplate restTemplate = new RestTemplate();

    private final List<String> supportedModels = List.of(
            "claude-opus-4-20250514",   // 最强，最贵
            "claude-sonnet-4-20250514",  // 性价比之选 ★推荐
            "claude-haiku-3-5-20241022"  // 最快最便宜
    );

    @Value("${anthropic.api.key:}")
    private String apiKey;

    @Value("${anthropic.version:2023-06-01}")
    private String apiVersion;

    @Value("${anthropic.model:claude-sonnet-4-20250514}")
    private String currentModel;

    @Override
    public List<String> getSupportedModels() { return supportedModels; }

    @Override
    public String getCurrentModel() { return currentModel; }

    @Override
    public void switchModel(String modelName) {
        if (!supportedModels.contains(modelName)) {
            throw new LlmException("Anthropic", modelName, "不支持。可用: " + supportedModels);
        }
        this.currentModel = modelName;
        log.info("[Claude] 已切换到模型: {}", modelName);
    }

    /**
     * Claude API 的请求格式与 OpenAI 不同：
     * - system 字段单独提取（不在 messages 里）
     * - max_tokens 是必填字段
     * - 响应格式: content[0].text
     */
    @Override
    public String chat(List<Map<String, String>> messages,
                       Double temperature, Integer maxTokens) {

        // 从 messages 中提取 system 消息（Claude 要求 system 单独传）
        String systemContent = "";
        List<Map<String, String>> userMessages = new java.util.ArrayList<>();
        for (Map<String, String> msg : messages) {
            if ("system".equals(msg.get("role"))) {
                systemContent = msg.get("content");
            } else {
                userMessages.add(msg);
            }
        }

        Map<String, Object> body = Map.of(
                "model", currentModel,
                "max_tokens", maxTokens != null ? maxTokens : 4096,
                "messages", userMessages,
                "temperature", temperature != null ? temperature : 0.7,
                "system", systemContent.isEmpty() ? "You are a helpful assistant." : systemContent
        );

        Map<String, Object> response = restTemplate.postForObject(API_URL, body, Map.class,
                Map.of(
                        "x-api-key", apiKey,
                        "anthropic-version", apiVersion,
                        "Content-Type", "application/json"
                ));

        // Claude 响应格式: content[0].text
        List<?> content = (List<?>) response.get("content");
        if (content == null || content.isEmpty()) {
            throw new LlmException("Anthropic", currentModel, "API返回空内容");
        }
        Map<?, ?> textBlock = (Map<?, ?>) content.get(0);
        return (String) textBlock.get("text");
    }

    @Override
    public void chatStream(List<Map<String, String>> messages,
                           Double temperature, Integer maxTokens,
                           StreamCallback callback) {
        // Claude 也支持 SSE 流式，简化处理同 GPT
        String fullResponse = chat(messages, temperature, maxTokens);
        int chunkSize = 20;
        for (int i = 0; i < fullResponse.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, fullResponse.length());
            callback.onToken(fullResponse.substring(i, end), end >= fullResponse.length());
        }
    }

    @Override
    public String getProviderName() { return "Anthropic (Claude)"; }

    @Override
    public String getDescription() {
        return "Anthropic Claude系列，长文本处理强，安全边界清晰";
    }
}
