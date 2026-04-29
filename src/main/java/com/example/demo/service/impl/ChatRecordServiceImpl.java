package com.example.demo.service.impl;
import com.example.demo.mapper.ChatRecordMapper;
import com.example.demo.entity.ChatRecord;
import com.example.demo.service.ChatRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ChatRecordServiceImpl implements ChatRecordService {
    @Autowired
    private ChatRecordMapper chatRecordMapper;

    /**
     * ✅ 新增 @Transactional：保存聊天记录是写操作，需要事务保护
     * rollbackFor = Exception.class：任何异常都回滚（不只是 RuntimeException）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(ChatRecord record){
        chatRecordMapper.save(record);
    }
    @Override
    public List<ChatRecord> getByUserId(int userId){
        return chatRecordMapper.getByUserId(userId);
    }
    public List<ChatRecord> getRecent(int userId, int limit) {
        return chatRecordMapper.getRecent(userId, limit);
    }
    @Override
    public List<ChatRecord> getBySessionId(int userId,int limit,String sessionId){
        return chatRecordMapper.getBySessionId(userId,sessionId,limit);
    }

}



