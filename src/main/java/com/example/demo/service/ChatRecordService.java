package com.example.demo.service;


import com.example.demo.entity.ChatRecord;

import java.util.List;

public interface ChatRecordService {

        void save(ChatRecord record);
        List<ChatRecord> getBySessionId(int userId,int limit,String sessionId);
        List<ChatRecord> getByUserId(int userId);
        List<ChatRecord> getRecent(int userId,int limit);
    }


