package com.example.demo.util;

import com.example.demo.websocket.AiStreamCallback;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;              // ✅ 新增：日志接口
import org.slf4j.LoggerFactory;        // ✅ 新增：日志工厂
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

@Component
public class AiUtil {

    // ✅ 新增：日志声明（原来缺失导致编译报错）
    private static final Logger log = LoggerFactory.getLogger(AiUtil.class);

    @Value("${ai.api.url}")
    private String API_URL;

    @Value("${ai.api.key}")           // ✅ 已修正：去掉了原来的 {$...} 错误写法
    private String API_KEY;

    @Value("${ai.api.model}")
    private String model;


    // ========== 1. 非流式聊天 ==========
    public String chat(List<Map<String, String>> messages) {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + API_KEY);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);          // ✅ 不再硬编码 "glm-4.5-air"

        // 传入消息列表（RestTemplate会自动序列化为JSON数组）
        body.put("messages", messages);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(API_URL, request, Map.class);

        List choices = (List) response.getBody().get("choices");
        Map choice = (Map) choices.get(0);
        Map msg = (Map) choice.get("message");

        return msg.get("content").toString();
    }


    // ========== 2. 流式聊天（SSE，带上下文）==========
    public String chatStreamWithContext(List<Map<String, String>> messages,
                                        SseEmitter emitter) {

        StringBuilder fullAnswer = new StringBuilder();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);      // ✅ 新增：连接超时10秒
            conn.setReadTimeout(60000);         // ✅ 新增：读取超时60秒

            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("model", model);
            bodyMap.put("stream", true);
            bodyMap.put("messages", messages);

            String body = objectMapper.writeValueAsString(bodyMap);

            // ✅ 改造：try-with-resources，自动关闭流，防资源泄漏
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            )) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String json = line.substring(5).trim();
                        if ("[DONE]".equals(json)) break;

                        Map<String, Object> responseMap =
                                objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

                        List<Map<String, Object>> choices =
                                (List<Map<String, Object>>) responseMap.get("choices");

                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> delta =
                                    (Map<String, Object>) choices.get(0).get("delta");

                            if (delta != null && delta.get("content") != null) {
                                String content = delta.get("content").toString();
                                emitter.send(SseEmitter.event().data(content));
                                fullAnswer.append(content);
                            }
                        }
                    }
                }
            }  // ✅ reader 自动关闭

            emitter.complete();

        } catch (Exception e) {
            log.error("AI流式调用失败(Context)", e);   // ✅ 原来这里语法错误已修正
            emitter.completeWithError(e);
        }

        return fullAnswer.toString();
    }


    // ========== 3. 带System Prompt的流式对话 ==========
    public String chatStreamWithSystemPrompt(String systemPrompt,
                                             List<Map<String, String>> messages,
                                             SseEmitter emitter) {

        StringBuilder fullAnswer = new StringBuilder();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);       // ✅ 新增
            conn.setReadTimeout(60000);          // ✅ 新增

            // 构造最终消息列表（system在最前面）
            List<Map<String, String>> finalMessages = new ArrayList<>();

            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                Map<String, String> sysMsg = new HashMap<>();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                finalMessages.add(sysMsg);
            }

            finalMessages.addAll(messages);

            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("model", model);
            bodyMap.put("stream", true);
            bodyMap.put("messages", finalMessages);

            String body = objectMapper.writeValueAsString(bodyMap);

            // ✅ 改造：try-with-resources
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            )) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String json = line.substring(5).trim();
                        if ("[DONE]".equals(json)) break;

                        Map<String, Object> responseMap =
                                objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

                        List<Map<String, Object>> choices =
                                (List<Map<String, Object>>) responseMap.get("choices");

                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> delta =
                                    (Map<String, Object>) choices.get(0).get("delta");

                            if (delta != null && delta.get("content") != null) {
                                String content = delta.get("content").toString();
                                emitter.send(SseEmitter.event().data(content));
                                fullAnswer.append(content);
                            }
                        }
                    }
                }
            }  // ✅ reader 自动关闭

            emitter.complete();

        } catch (Exception e) {
            log.error("AI流式调用失败(SystemPrompt)", e);   // ✅ 原来：完全静默吞异常，现在补上日志
            emitter.completeWithError(e);
        }

        return fullAnswer.toString();
    }


    // ========== 4. 通用流式对话（WebSocket回调方式）==========
    public String chatStreamWithCallback(List<Map<String, String>> messages,
                                         String systemPrompt,
                                         AiStreamCallback callback) {

        StringBuilder fullAnswer = new StringBuilder();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);       // ✅ 已有（不用改）
            conn.setReadTimeout(60000);          // ✅ 已有（不用改）

            List<Map<String, String>> finalMessages = new ArrayList<>();

            if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                Map<String, String> sysMsg = new HashMap<>();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                finalMessages.add(sysMsg);
            }

            finalMessages.addAll(messages);

            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("model", model);
            bodyMap.put("stream", true);
            bodyMap.put("messages", finalMessages);

            String body = objectMapper.writeValueAsString(bodyMap);

            // ✅ 改造：try-with-resources（原来缺少）
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            )) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String json = line.substring(5).trim();
                        if ("[DONE]".equals(json)) break;

                        Map<String, Object> responseMap =
                                objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

                        List<Map<String, Object>> choices =
                                (List<Map<String, Object>>) responseMap.get("choices");

                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> delta =
                                    (Map<String, Object>) choices.get(0).get("delta");

                            if (delta != null && delta.get("content") != null) {
                                String content = delta.get("content").toString();
                                callback.onChunk(content);
                                fullAnswer.append(content);
                            }
                        }
                    }
                }
            }  // ✅ reader 自动关闭

        } catch (Exception e) {
            log.error("AI流式调用失败(Callback)", e);   // ✅ 补充日志
            throw new RuntimeException("AI流式调用失败: " + e.getMessage(), e);
        }

        return fullAnswer.toString();
    }


    // ========== 5. 简单流式（仅问题，无上下文）==========
    public void chatStream(String question, SseEmitter emitter) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);       // ✅ 新增
            conn.setReadTimeout(60000);          // ✅ 新增

            String body = "{\n" +
                    "  \"model\": \"" + model + "\",\n" +
                    "  \"stream\": true,\n" +
                    "  \"messages\": [\n" +
                    "    {\"role\": \"user\", \"content\": \"" + escapeJson(question) + "\"}\n" +
                    "  ]\n" +
                    "}";

            // ✅ 改造：try-with-resources
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            )) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String json = line.substring(5).trim();
                        if ("[DONE]".equals(json)) break;

                        Map<String, Object> responseMap = objectMapper.readValue(json,
                                new TypeReference<Map<String, Object>>() {});

                        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map<String, Object> choice = choices.get(0);
                            Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                            if (delta != null && delta.containsKey("content")) {
                                String content = (String) delta.get("content");
                                if (content != null && !content.isEmpty()) {
                                    emitter.send(SseEmitter.event().data(content));
                                }
                            }
                        }
                    }
                }
            }  // ✅ reader 自动关闭

            emitter.complete();

        } catch (Exception e) {
            log.error("AI简单流式调用失败", e);    // ✅ 原来：静默吞异常
            emitter.completeWithError(e);
        }
    }


    // ========== 工具方法 ==========
    private  String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
// ✅ 删除了文件末尾393-440行的死代码（注释掉的旧chat方法）
