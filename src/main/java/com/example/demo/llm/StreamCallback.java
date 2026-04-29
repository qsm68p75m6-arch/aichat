package com.example.demo.llm;

import java.io.IOException;

/**
 * 流式回调接口
 * LLM 每生成一个 token 就回调一次
 */
@FunctionalInterface
public interface StreamCallback {

    /**
     * @param token     当前生成的文本片段
     * @param isComplete 是否是最后一个 token（流结束标记）
     */
    void onToken(String token, boolean isComplete) throws IOException;
}
