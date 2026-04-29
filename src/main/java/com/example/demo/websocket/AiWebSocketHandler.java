package com.example.demo.websocket;

import com.example.demo.common.JwtUtil;
import com.example.demo.entity.ChatRecord;
import com.example.demo.entity.SystemPrompt;
import com.example.demo.service.ChatRecordService;
import com.example.demo.service.SystemPromptService;
import com.example.demo.util.AiUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AiWebSocketHandler extends TextWebSocketHandler {
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ChatRecordService chatRecordService;

    @Autowired
    private SystemPromptService systemPromptService;

    /**
     * 在线连接存储
     * key = sessionId（WebSocket会话ID）
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 存储用户ID映射（用于后续推送通知等扩展）
     * key = userId, value = Set<sessionId>
     */
    private final Map<Integer, Set<String>> userSessions = new ConcurrentHashMap<>();

    // ==================== 1. 连接建立 ====================

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从握手请求中提取 token（通过 query parameter）
        Integer userId = extractUserId(session);

        if (userId == null) {
            // 未认证，发送错误并关闭
            sendJson(session, Map.of("type", "error", "message", "未登录或token无效"));
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // 存储用户信息到 session attributes
        session.getAttributes().put("userId", userId);

        // 加入在线列表
        sessions.put(session.getId(), session);
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session.getId());

        log.info("[WS] 用户{} 已连接, sessionId={}", userId, session.getId());

        // 连接成功确认
        sendJson(session, Map.of("type", "connected", "message", "连接成功"));
    }

    // ==================== 2. 收到消息（核心业务）====================

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Integer userId = (Integer) session.getAttributes().get("userId");

            // 解析客户端发来的 JSON
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> request = mapper.readValue(message.getPayload(), Map.class);

            String question = (String) request.get("question");
            String sessionId = (String) request.getOrDefault("sessionId", "");
            Integer promptId = request.get("promptId") != null ?
                    Integer.valueOf(request.get("promptId").toString()) : null;

            if (question == null || question.trim().isEmpty()) {
                sendJson(session, Map.of("type", "error", "message", "问题不能为空"));
                return;
            }

            // 自动生成 sessionId
            if (sessionId == null || sessionId.trim().isEmpty()) {
                sessionId = UUID.randomUUID().toString().replace("-", "");
            }

            log.info("[WS] 用户{} 提问: {}, sessionId={}, promptId={}",
                    userId, question.substring(0, Math.min(20, question.length())) + "...", sessionId, promptId);

            // 查询该 session 的历史记录
            List<ChatRecord> history = chatRecordService.getBySessionId(userId, 20, sessionId);

            // 构造 messages 列表
            List<Map<String, String>> messages = new ArrayList<>();
            Collections.reverse(history); // 时间正序

            for (ChatRecord record : history) {
                Map<String, String> userMsg = new HashMap<>();
                userMsg.put("role", "user");
                userMsg.put("content", record.getQuestion());

                Map<String, String> aiMsg = new HashMap<>();
                aiMsg.put("role", "assistant");
                aiMsg.put("content", record.getAnswer());

                messages.add(userMsg);
                messages.add(aiMsg);
            }

            // 加入当前问题
            Map<String, String> current = new HashMap<>();
            current.put("role", "user");
            current.put("content", question);
            messages.add(current);

            // 查询角色设定（如果有）
            String systemPromptText = null;
            if (promptId != null && promptId > 0) {
                SystemPrompt sp = systemPromptService.getById(promptId);
                if (sp != null) {
                    systemPromptText = sp.getSystemPrompt();
                }
            }

            // 通知前端：开始生成
            sendJson(session, Map.of("type", "start", "sessionId", sessionId));

            // 🔥 调用 AI（通过回调逐段推送）
            String fullAnswer = AiUtil.chatStreamWithCallback(
                    messages,
                    systemPromptText,
                    chunk -> {
                        // 每收到一段AI回复，立即推送给前端
                        sendJson(session, Map.of(
                                "type", "chunk",
                                "content", chunk
                        ));
                    }
            );

            // 🔥 保存聊天记录
            ChatRecord record = new ChatRecord();
            record.setUserId(userId);
            record.setQuestion(question);
            record.setAnswer(fullAnswer);
            record.setCreateTime(new Date());
            record.setSessionId(sessionId);
            chatRecordService.save(record);

            // 通知前端：完成（附带 sessionId 和完整回答）
            sendJson(session, Map.of(
                    "type", "done",
                    "sessionId", sessionId,
                    "fullAnswer", fullAnswer
            ));

            log.info("[WS] 用户{} 本次问答完成, sessionId={}", userId, sessionId);

        } catch (Exception e) {
            log.error("[WS] 处理消息异常", e);
            sendJson(session, Map.of("type", "error", "message", "服务异常: " + e.getMessage()));
        }
    }

    // ==================== 3. 传输错误 ====================

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("[WS] 传输异常, sessionId={}: {}", session.getId(), exception.getMessage(), exception);
        cleanupSession(session);
    }

    // ==================== 4. 连接关闭 ====================

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("[WS] 连接关闭, sessionId={}, status={}", session.getId(), status);
        cleanupSession(session);
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 WebSocket 握手请求中提取 userId
     * 前端连接时带上 token: ws://host/ws/chat?token=xxxxx
     */
    private Integer extractUserId(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) return null;

            String query = uri.getQuery();
            if (query == null || query.isEmpty()) return null;

            // 解析 query 参数获取 token
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && "token".equals(kv[0])) {
                    String token=java.net.URLDecoder.decode(kv[1]);

                    return jwtUtil.getUserIdFromToken(token);
                }
            }
        } catch (Exception e) {
            log.error("[WS] 提取userId失败", e);
        }
        return null;
    }

    /**
     * 发送 JSON 消息给客户端
     */
    private void sendJson(WebSocketSession session, Map<String, Object> data) {
        try {
            if (session.isOpen()) {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(data);
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.error("[WS] 发送消息失败", e);
        }
    }

    /**
     * 清理断开的 session
     */
    private void cleanupSession(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.remove(sessionId);

        Integer userId = (Integer) session.getAttributes().get("userId");
        if (userId != null) {
            Set<String> userSet = userSessions.get(userId);
            if (userSet != null) {
                userSet.remove(sessionId);
                if (userSet.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
        }
    }

    /**
     * 获取当前在线人数（供管理端使用）
     */
    public int getOnlineCount() {
        return sessions.size();
    }
}
