package com.ruoyi.system.ai.service;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.system.domain.ConversationHistory;
import com.ruoyi.system.domain.ConversationSession;
import com.ruoyi.system.mapper.ConversationHistoryMapper;
import com.ruoyi.system.mapper.ConversationSessionMapper;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Executable contract test for transaction-aware Redis chat cache writes. */
public final class ConversationServiceTransactionTest
{
    public static void main(String[] args) throws Exception
    {
        RecordingRedisCache redis = new RecordingRedisCache();
        ConversationService service = new ConversationService();
        inject(service, "historyMapper", new RecordingHistoryMapper());
        inject(service, "sessionMapper", new RecordingSessionMapper());
        inject(service, "redisCache", redis);

        List<TransactionSynchronization> rollbackSynchronizations = saveInsideTransaction(
            service, "rollback-message");
        require(redis.writes == 0, "Redis was written before transaction completion");
        complete(rollbackSynchronizations, false);
        require(redis.writes == 0, "rolled-back transaction left a Redis message");

        List<TransactionSynchronization> commitSynchronizations = saveInsideTransaction(
            service, "commit-message");
        require(redis.writes == 0, "Redis was written before commit");
        complete(commitSynchronizations, true);
        require(redis.writes == 1, "committed transaction did not update Redis");

        service.saveMessage(7L, "session-1", "user", "immediate-message", null);
        require(redis.writes == 2, "non-transactional save did not update Redis immediately");
    }

    private static List<TransactionSynchronization> saveInsideTransaction(
        ConversationService service, String content)
    {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try
        {
            service.saveMessage(7L, "session-1", "user", content, null);
            return TransactionSynchronizationManager.getSynchronizations();
        }
        finally
        {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static void complete(List<TransactionSynchronization> synchronizations, boolean commit)
    {
        if (commit)
        {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        int status = commit ? TransactionSynchronization.STATUS_COMMITTED
            : TransactionSynchronization.STATUS_ROLLED_BACK;
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingRedisCache extends RedisCache
    {
        private int writes;
        private String value;

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getCacheObject(String key)
        {
            return (T) value;
        }

        @Override
        public <T> void setCacheObject(String key, T value, Integer timeout, TimeUnit timeUnit)
        {
            writes++;
            this.value = String.valueOf(value);
        }
    }

    private static final class RecordingHistoryMapper implements ConversationHistoryMapper
    {
        @Override public int insertHistory(ConversationHistory history) { return 1; }
        @Override public List<ConversationHistory> selectBySession(Long userId, String sessionId) { return List.of(); }
        @Override public List<Map<String, Object>> selectSessionsByUser(Long userId) { return List.of(); }
        @Override public List<ConversationHistory> selectRecentByUser(Long userId, int limit) { return List.of(); }
        @Override public int softDeleteSession(Long userId, String sessionId) { return 0; }
    }

    private static final class RecordingSessionMapper implements ConversationSessionMapper
    {
        @Override public int insertIfAbsent(ConversationSession session) { return 1; }
        @Override public int updateAfterMessage(Long userId, String sessionId, String role, String content) { return 1; }
        @Override public List<Map<String, Object>> selectSessionsByUser(Long userId) { return List.of(); }
        @Override public int deleteByUserAndSession(Long userId, String sessionId) { return 0; }
    }
}
