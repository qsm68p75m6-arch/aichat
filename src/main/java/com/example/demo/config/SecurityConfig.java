package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity   // ✅ 新增：显式启用WebSecurity
public class SecurityConfig {

    /**
     * BCrypt密码编码器
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤器链配置
     *
     * 设计思路：
     * - 登录/注册：permitAll（无需token）
     * - WebSocket：permitAll（WS端自己校验token）
     * - 静态资源：permitAll（CSS/JS/图片等）
     * - 其余所有接口：authenticated（必须带有效JWT）
     *
     * ⚠️ 注意：这里只做URL级别的拦截。
     *    实际的JWT验证由 JwtInterceptor（或后续改造的 JwtFilter）完成。
     *    这里 authenticated() 的作用是：未认证请求会被Spring Security拦截返回403，
     *    作为最后一道防线，防止绕过Interceptor的情况。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF（前后端分离项目，用Token而非Session，不需要CSRF防护）
                .csrf(csrf -> csrf.disable())

                // 配置请求授权规则
                .authorizeHttpRequests(auth -> auth

                        // ===== 公开接口（无需登录）=====

                        // 用户相关：注册 + 登录
                        .requestMatchers(
                                "/users",           // POST 注册
                                "/users/login"      // POST 登录
                        ).permitAll()

                        // WebSocket（WS握手端点自己校验token）
                        .requestMatchers("/ws/**").permitAll()

                        // 静态资源
                        .requestMatchers(
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/error"
                        ).permitAll()

                        // H2控制台（如果用了的话）
                        .requestMatchers("/h2-console/**").permitAll()

                        // ===== 其余所有接口：需要认证 =====
                        .anyRequest().authenticated()
                );

        // ✅ 可选：如果暂时不想强制拦截所有请求（开发阶段），
        // 可以先用下面这行替代上面的 anyRequest().authenticated()，
        // 但上线前务必改回 authenticated()
        // .anyRequest().permitAll()

        return http.build();
    }
}
