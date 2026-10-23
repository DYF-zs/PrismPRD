package com.prismprd.prd.chat;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface ChatMessageMapper {

    ChatMessage selectById(@Param("id") UUID id);

    void insert(ChatMessage message);

    List<ChatMessage> selectBySession(@Param("sessionId") UUID sessionId);

    /** 最近 N 条消息，按时间正序返回（供 Agent 运行时加载记忆） */
    List<ChatMessage> selectRecent(@Param("sessionId") UUID sessionId, @Param("limit") int limit);
}
