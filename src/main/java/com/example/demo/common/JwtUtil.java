package com.example.demo.common;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import static io.jsonwebtoken.Claims.EXPIRATION;

@Component
public class JwtUtil {

   @Value("${jwt.secret}")
   private String secret;
   @Value("${jwt.expiration}")
   private long expiration;
   private  Key KEY;
   @PostConstruct
   public void init(){
       this.KEY=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
   }
    // 🔐 生成 token
    public  String generateToken(Integer userId, String username) {
        return Jwts.builder()
                .setSubject(username)
                .claim("id", userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    // 🔍 解析 token
    public  Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 👤 从token中提取用户ID
    public  Integer getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("id", Integer.class);
    }
}

