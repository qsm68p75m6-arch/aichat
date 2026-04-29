package com.example.demo.config;
import com.example.demo.common.Result;
import com.example.demo.common.JwtUtil;
import com.example.demo.common.JwtUtil;
import com.example.demo.common.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
public class JwtInterceptor implements HandlerInterceptor {
    private static  final  Logger log=LoggerFactory.getLogger(JwtInterceptor.class);
    @Autowired
    private JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public JwtInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 只放行 POST /users（注册）和 POST /users/login（登录），其他方法都需要 token
        if ("POST".equals(method)&&
                ("/users".equals(uri)||"/users/login".equals(uri))) {
            return true;
        }

        // 1. 获取 token（从请求头）
        String token = request.getHeader("Authorization");

        // 2. 判断是否存在
        if (token == null || token.isEmpty()) {
            writeJson(response, Result.fail("未登录，请先登录"));
            return false;
        }

        try {
            // 3. 解析 token
            Claims claims = jwtUtil.parseToken(token);

            // 获取 userId（JWT 中数字可能反序列化为 Long，需要转为 Integer）
            Object idObj = claims.get("id");
            log.debug("JWT解析：idobj={},type={}",idObj,idObj !=null?idObj.getClass().getName():"null");
            if (idObj != null) {
                request.setAttribute("userId",((Number)idObj).intValue());
            } else {
                log.warn("JWT警告：token中id字段为null");
            }
            request.setAttribute("username", claims.getSubject());

            return true;

        } catch (Exception e) {
            log.error("JWT解析失败",e);
            writeJson(response,Result.fail("token无效或已过期"));
            return false;
        }
    }
    private void writeJson(HttpServletResponse response,Result result) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(401);
        String json=objectMapper.writeValueAsString(result);
        response.getWriter().write(json);
    }
}
