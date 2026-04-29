package com.example.demo.websocket;

@FunctionalInterface
public interface AiStreamCallback {

    /**
     * 当AI返回一段内容时调用
     * @param chunk AI返回的一小段文本
     * @throws Exception 发送异常
     */
    void onChunk(String chunk) throws Exception;
}