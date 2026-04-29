package com.example.demo.llm;

/**
 * 模型异常：当模型调用失败、模型不支持、API Key 无效等时抛出
 */
public class LlmException extends RuntimeException {

    private final String provider;
    private final String model;

    public LlmException(String provider, String model, String message) {
        super(String.format("[%s/%s] %s", provider, model, message));
        this.provider = provider;
        this.model = model;
    }

    public LlmException(String provider, String model, String message, Throwable cause) {
        super(String.format("[%s/%s] %s", provider, model, message), cause);
        this.provider = provider;
        this.model = model;
    }

    public String getProvider() { return provider; }
    public String getModel() { return model; }
}
