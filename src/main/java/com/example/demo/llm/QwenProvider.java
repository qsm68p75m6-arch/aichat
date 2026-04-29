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
 * 阿里通义千问 Qwen 模型提供者
 * DashScope API（也兼容部分 OpenAI 格式）
 */
@Component
@ConditionalOnProperty(name = "qwen.api.key")
public class QwenProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(QwenProvider.class);
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
    // 也支持 OpenAI 兼容模式:
    // private static final String OPENAI_COMPAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final RestTemplate restTemplate = new RestTemplate();

    private final List<String> supportedModels = List.of(
            "qwen-turbo",       // 快速便宜 ★推荐
            "qwen-plus",        // 更强
            "qwen-max",         // 最强
            "qwen-max-longcontext",  // 长文本版（30K+上下文）
            "qwen-vl-max"       // 多模态视觉版
    );

    @Value("${qwen.api.key:}")
    private String apiKey;

    @Value("${qwen.model:qwen-turbo}")
    private String currentModel;

    @Override
    public List<String> getSupportedModels() { return supportedModels; }

    @Override
    public String getCurrentModel() { return currentModel; }

    @Override
    public void switchModel(String modelName) {
        if (!supportedModels.contains(modelName)) {
            throw new LlmException("Qwen", modelName, "不支持。可用: " + supportedModels);
        }
        this.currentModel = modelName;
        log.info("[Qwen] 已切换到模型: {}", modelName);
    }

    @Override
    public String chat(List<Map<String, String>> messages,
                       Double temperature, Integer maxTokens) {
        // 使用 DashScope 原生格式
        Map<String, Object> input = Map.of("messages", messages);

        Map<String, Object> body = Map.of(
                "model", currentModel,
                "input", input,
                "parameters", Map.of(
                        "temperature", temperature != null ? temperature : 0.7,
                        "max_tokens", maxTokens != null ? maxTokens : 4096,
                        "result_format", "message"  // 返回消息格式
                )
        );

        Map<String, Object> response = restTemplate.postForObject(API_URL, body, Map.class,
                Map.of("Authorization", "Bearer " + apiKey));

        // DashScope 响应格式: output.choices[0].message.content
        Map<?, ?> output = (Map<?, ?>) response.get("output");
        List<?> choices = (List<?>) output.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException("Qwen", currentModel, "API返回空结果");
        }
        Map<?, ?> choice = (Map<?, ?>) choices.get(0);
        Map<?, ?> message = (Map<?, ?>) choice.get("message");
        return (String) message.get("content");
    }

    @Override
    public void chatStream(List<Map<String, String>> messages,
                           Double temperature, Integer maxTokens,
                           StreamCallback callback) {
        String fullResponse = chat(messages, temperature, maxTokens);
        int chunkSize = 20;
        for (int i = 0; i < fullResponse.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, fullResponse.length());
            callback.onToken(fullResponse.substring(i, end), end >= fullResponse.length());
        }
    }

    @Override
    public String getProviderName() { return "阿里通义千问 (Qwen)"; }

    @Override
    public String getDescription() {
        return "阿里通义千问系列，中文能力优秀，长上下文强，国产合规";
    }
}
