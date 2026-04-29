package com.example.demo.llm;

import java.util.List;
import java.util.Map;

/**
 * LLM 模型提供者接口
 * 所有大模型（GPT/Claude/GLM/DeepSeek/Qwen）都实现此接口
 * 实现统一的调用方式，方便切换和扩展
 */
public interface LlmProvider {

    /**
     * 获取该提供者支持的模型名称列表
     */
    List<String> getSupportedModels();

    /**
     * 获取当前正在使用的模型名称
     */
    String getCurrentModel();

    /**
     * 切换到指定模型
     * @param modelName 模型名称（必须是 getSupportedModels() 中的之一）
     */
    void switchModel(String modelName);

    /**
     * 发送聊天请求（非流式）
     *
     * @param messages 对话消息列表
     * @param temperature 温度参数（0-2，越高越有创造性）
     * @param maxTokens 最大生成 token 数
     * @return 模型的文本回复
     */
    String chat(List<Map<String, String>> messages, Double temperature, Integer maxTokens);

    /**
     * 发送流式聊天请求（SSE）
     *
     * @param messages       对话消息列表
     * @param temperature    温度参数
     * @param maxTokens      最大 token 数
     * @param streamCallback 流式回调，每收到一个 token 就调用一次
     */
    void chatStream(List<Map<String, String>> messages,
                    Double temperature,
                    Integer maxTokens,
                    StreamCallback streamCallback);

    /**
     * 获取提供者的显示名称（用于前端展示）
     */
    String getProviderName();

    /**
     * 获取该提供者的描述信息（用于 Swagger/API 文档）
     */
    String getDescription();
}
