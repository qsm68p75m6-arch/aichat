package com.example.demo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简易速率限制拦截器（内存级）
 *
 * 原理：基于滑动窗口计数器，每个 IP 在时间窗口内允许的最大请求数。
 *
 * 为什么用 ConcurrentHashMap + AtomicInteger？
 * - 多个用户同时访问时，不同线程会同时读写这些变量
 * - ConcurrentHashMap 保证线程安全（不会数据错乱）
 * - AtomicInteger 保证计数操作是原子的（不会丢失）
 *
 * 限流规则：
 * - AI 接口 (/ai/**)     ：每分钟最多 20 次 ← 最严格，因为花钱
 * - 登录接口 (含login)    ：每分钟最多 10 次 ← 防暴力破解
 * - 其他接口              ：不限制
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    // ====== 核心存储（两个Map配合使用）======

    /**
     * 请求计数器
     * key = "IP:请求路径"
     * value = 当前窗口内的已执行请求次数
     */
    private final Map<String, AtomicInteger> requestCount = new ConcurrentHashMap<>();

    /**
     * 窗口起始时间
     * key = "IP:请求路径"
     * value = 当前窗口开始的毫秒时间戳
     */
    private final Map<String, AtomicLong> windowStart = new ConcurrentHashMap<>();

    // ====== 限流规则配置（改这里就能调整限制）======
    private static final int AI_MAX_REQUESTS = 20;        // AI 接口上限
    private static final int LOGIN_MAX_REQUESTS = 10;      // 登录接口上限
    private static final long WINDOW_MS = 60_000L;          // 窗口时长 60秒

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        String uri = request.getRequestURI();
        String clientIp = getClientIp(request);
        String key = clientIp + ":" + uri;

        // ① 判断这个接口要不要限流
        int maxRequests = getMaxRequests(uri);
        if (maxRequests <= 0) {
            return true; // 不限流 → 直接放行
        }

        // ② 获取或创建当前窗口的计数器和时间戳
        long now = System.currentTimeMillis();
        AtomicLong startTime = windowStart.computeIfAbsent(key, k -> new AtomicLong(now));
        AtomicInteger count = requestCount.computeIfAbsent(key, k -> new AtomicInteger(0));

        // ③ 加锁检查（防止并发问题导致多放行几个）
        synchronized (this) {
            // 如果距离窗口开始已超过 60 秒 → 重置为新窗口
            if (now - startTime.get() > WINDOW_MS) {
                startTime.set(now);     // 新窗口开始时间
                count.set(0);           // 计数归零
            }

            // ④ 计数 + 1 后判断是否超限
            if (count.incrementAndGet() > maxRequests) {
                log.warn("速率限制触发: ip={}, uri={}, count={}/{}", clientIp, uri, count.get(), maxRequests);

                response.setStatus(429);                          // HTTP 状态码: Too Many Requests
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(
                        "{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\",\"data\":null}"
                );
                return false;                                    // 拦截，不放行
            }
        }

        return true;  // 未超限 → 放行
    }

    /**
     * 根据 URI 决定该接口的最大请求次数
     * 返回 <= 0 表示不限流
     */
    private int getMaxRequests(String uri) {
        if (uri.contains("/ai/") || uri.contains("/ws/chat")) {
            return AI_MAX_REQUESTS;      // AI 接口最严格
        }
        if (uri.contains("login")) {
            return LOGIN_MAX_REQUESTS;    // 登录接口次之
        }
        return 0;                        // 其他接口不限流
    }

    /**
     * 获取客户端真实 IP
     *
     * 为什么这么复杂？因为你的请求可能经过代理/Nginx/网关：
     * - 直接访问 → 用 getRemoteAddr() 就够了
     * - 经过了 Nginx 反向代理 → 看 X-Real-IP 头
     * - 经过了多层代理 → 看 X-Forwarded-For 头（可能多个IP逗号分隔）
     */
    private String getClientIp(HttpServletRequest request) {
        // 优先级1: X-Forwarded-For（标准头，记录经过的所有代理IP）
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 优先级2: X-Real-IP（Nginx 常用的头）
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            // 兜底: 直接获取 TCP 连接的来源 IP
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能是 "client, proxy1, proxy2" 格式
        // 取第一个才是真实客户端 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
