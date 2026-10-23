package com.prismprd.prd.chat;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ChatSessionMapper {

    void insert(ChatSession session);

    ChatSession selectById(@Param("id") UUID id);

    List<ChatSession> selectByPrd(@Param("prdId") UUID prdId);

    /** 更新会话活跃时间（有新消息时调用） */
    void touchUpdated(@Param("id") UUID id);
}
