package com.ruoyi.system.mapper;

import java.util.List;
import java.util.Map;
import com.ruoyi.system.domain.ConversationSession;
import org.apache.ibatis.annotations.Param;

/** Mapper for durable conversation sessions and backward-compatible session lists. */
public interface ConversationSessionMapper
{
    int insertIfAbsent(ConversationSession session);

    int updateAfterMessage(@Param("userId") Long userId,
                           @Param("sessionId") String sessionId,
                           @Param("role") String role,
                           @Param("content") String content);

    List<Map<String, Object>> selectSessionsByUser(Long userId);

    int deleteByUserAndSession(@Param("userId") Long userId,
                               @Param("sessionId") String sessionId);
}
