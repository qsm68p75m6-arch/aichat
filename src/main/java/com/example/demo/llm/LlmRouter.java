package com.example.demo.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型路由器 —— 核心调度中心
 *
 * 职责：
 * 1. 自动收集所有已注册的 LlmProvider
 * 2. 根据请求参数或配置选择使用哪个模型
 * 3. 支持运行时动态切换
 * 4. 提供统一的外部调用入口
 */
@Component
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    /**
     * Spring 自动注入所有实现了 LlmProvider 接口的 Bean
     * 包括 GlmProvider, GptProvider, ClaudeProvider, DeepSeekProvider, QwenProvider...
     */
    private final List<LlmProvider> providers;

    /**
     * 默认模型提供者名称（从配置文件读取）
     * 可选值: glm / openai / anthropic / deepseek / qwen
     */
    @Value("${llm.default-provider:glm}")
    private String defaultProviderName;

    /**
     * provider 名称 → 实例 的映射表
     */
    private final Map<String, LlmProvider> providerMap = new ConcurrentHashMap<>();

    public LlmRouter(List<LlmProvider> providers) {
        this.providers = providers;
    }

    /**
     * 初始化：构建 provider 映射表
     * 启动时自动执行
     */
    @PostConstruct
    public void init() {
        for (LlmProvider provider : providers) {
            String key = toKey(provider);
            providerMap.put(key, provider);
            log.info("🤖 注册模型提供者: {} | 当前模型: {} | 支持模型: {}",
                    key, provider.getCurrentModel(), provider.getSupportedModels());
        }

        if (providerMap.isEmpty()) {
            log.warn("⚠️ 未注册任何 LLM Provider！请至少配置一个模型的 API Key");
        }
    }

    // ==================== 公开 API ====================

    /**
     * 发送聊天请求（根据 provider 选择对应模型）
     *
     * @param providerName 模型提供者名称，null 则用默认
     * @param modelName    具体模型名（可选，不传则用该 provider 当前默认）
     * @param messages     对话消息
     * @param temperature  温度
     * @param maxTokens    最大 token
     * @return 模型回复文本
     */
    public String chat(String providerName, String modelName,
                       List<Map<String, String>> messages,
                       Double temperature, Integer maxTokens) {

        LlmProvider provider = resolveProvider(providerName);
        
        // 如果指定了具体子模型，先切换过去
        if (modelName != null && !modelName.equals(provider.getCurrentModel())) {
            provider.switchModel(modelName);
        }

        log.info("[{}] 使用模型 {} 处理请求 (messages={})",
                getProviderName(), provider.getCurrentModel(), messages.size());

        return provider.chat(messages, temperature, maxTokens);
    }

    /**
     * 流式聊天请求
     */
    public void chatStream(String providerName, String modelName,
                           List<Map<String, String>> messages,
                           Double temperature, Integer maxTokens,
                           StreamCallback callback) {

        LlmProvider provider = resolveProvider(providerName);

        if (modelName != null && !modelName.equals(provider.getCurrentModel())) {
            provider.switchModel(modelName);
        }

        log.info("[{}] 使用模型 {} 处理流式请求",
                getProviderName(), provider.getCurrentModel());

        provider.chatStream(messages, temperature, maxTokens, callback);
    }

    // ==================== 查询 API ====================

    /** 获取所有已注册的提供者及其可用模型列表 */
    public Map<String, Object> getAvailableProviders() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, LlmProvider> entry : providerMap.entrySet()) {
            LlmProvider p = entry.getValue();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", p.getProviderName());
            info.put("description", p.getDescription());
            info.put("current_model", p.getCurrentModel());
            info.put("supported_models", p.getSupportedModels());
            result.put(entry.getKey(), info);
        }
        return result;
    }

    /** 获取当前默认提供者 */
    public String getDefaultProviderName() { return defaultProviderName; }

    /** 设置默认提供者（运行时切换） */
    public void setDefaultProvider(String name) {
        if (!providerMap.containsKey(name)) {
            throw new LlmException("Router", name, "未注册此提供者。可选: " + providerMap.keySet());
        }
        this.defaultProviderName = name;
        log.info("默认模型提供者已切换为: {}", name);
    }

    /**
     * 切换指定 provider 的子模型
     * @param providerName 提供者名称 (glm/openai/anthropic/deepseek/qwen)
     * @param modelName    要切换到的子模型名称
     */
    public void switchSubModel(String providerName, String modelName) {
        LlmProvider provider = resolveProvider(providerName);
        provider.switchModel(modelName);
        log.info("[{}] 子模型已切换为: {}", providerName, modelName);
    }

    /** 所有注册的提供者名称 */
    public Set<String> getRegisteredProviderNames() {
        return Collections.unmodifiableSet(providerMap.keySet());
    }

    // ==================== 内部方法 ====================

    /**
     * 解析要使用的 Provider：
     * 1. 如果传了 providerName 且已注册 → 用它
     * 2. 否则 → 用默认的
     */
    private LlmProvider resolveProvider(String providerName) {
        if (providerName != null && !providerName.isEmpty()) {
            LlmProvider p = providerMap.get(providerName);
            if (p == null) {
                throw new LlmException("Router", providerName,
                        "未注册。可用的: " + providerMap.keySet());
            }
            return p;
        }
        
        LlmProvider def = providerMap.get(defaultProviderName);
        if (def == null) {
            // 默认的没找到 → 用第一个可用的
            if (providerMap.isEmpty()) {
                throw new LlmException("Router", "none", "没有可用的模型提供者！");
            }
            def = providerMap.values().iterator().next();
            log.warn("默认提供者 '{}' 未找到，回退到: {}", defaultProviderName, def.getProviderName());
        }
        return def;
    }

    /**
     * 从 Provider 实例推断其 key 名称
     * 规则: 类名小写去掉 Provider 后缀
     *   GlmProvider → "glm"
     *   GptProvider → "openai"
     *   ClaudeProvider → "anthropic" （特殊映射）
     */
    private String toKey(LlmProvider provider) {
        String simpleName = provider.getClass().getSimpleName();
        // 特殊映射
        if (simpleName.contains("Gpt")) return "openai";
        if (simpleName.contains("Claude")) return "anthropic";
        // 通用规则: XxxProvider -> xxx
        return simpleName.replace("Provider", "").toLowerCase();
    }

    private String getProviderName() {
        return defaultProviderName;
    }
}
