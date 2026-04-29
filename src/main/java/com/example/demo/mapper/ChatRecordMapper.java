package com.example.demo.mapper;

import com.example.demo.entity.ChatRecord;

import java.util.List;

public interface ChatRecordMapper {

    void save(ChatRecord record);
    List<ChatRecord> getRecent(int userId,int limit);
    List<ChatRecord> getBySessionId(int userid,String sessionId,int limit);

    List<ChatRecord> getByUserId(int userId);
}

