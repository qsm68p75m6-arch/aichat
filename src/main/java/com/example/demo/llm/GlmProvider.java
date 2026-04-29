package com.example.demo.llm;

import com.example.demo.util.AiUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 智谱 GLM 模型提供者
 * 你项目当前使用的模型
 */
@Component
public class GlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(GlmProvider.class);

    private final List<String> supportedModels = List.of(
            "glm-4-flash",      // 便宜快速，适合简单任务
            "glm-4-air",        // 你当前在用的 ★
            "glm-4-plus",       // 更强但更贵
            "glm-4"             // 最强版本
    );

    @Value("${ai.api.model:glm-4-air}")
    private String currentModel;

    @Override
    public List<String> getSupportedModels() {
        return supportedModels;
    }

    @Override
    public String getCurrentModel() { return currentModel; }

    @Override
    public void switchModel(String modelName) {
        if (!supportedModels.contains(modelName)) {
            throw new LlmException("GLM", modelName,
                    "不支持此模型。可用: " + supportedModels);
        }
        this.currentModel = modelName;
        log.info("[GLM] 已切换到模型: {}", modelName);
    }

    @Override
    public String chat(List<Map<String, String>> messages,
                       Double temperature, Integer maxTokens) {
        try {
            return AiUtil.chatWithContext(messages, currentModel);
        } catch (Exception e) {
            throw new LlmException("GLM", currentModel, "调用失败", e);
        }
    }

    @Override
    public void chatStream(List<Map<String, String>> messages,
                           Double temperature,
                           Integer maxTokens,
                           StreamCallback streamCallback) {
        try {
            AiUtil.chatStreamWithContext(messages, streamCallback::onToken, currentModel);
        } catch (Exception e) {
            throw new LlmException("GLM", currentModel, "流式调用失败", e);
        }
    }

    @Override
    public String getProviderName() { return "智谱 AI (Zhipu/GLM)"; }

    @Override
    public String getDescription() {
        return "智谱AI大语言模型系列，国产合规，中文能力强";
    }
}
