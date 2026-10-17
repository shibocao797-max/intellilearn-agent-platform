-- Persist conversations before their first message and backfill legacy message-only sessions.
CREATE TABLE IF NOT EXISTS conversation_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    title VARCHAR(80) DEFAULT NULL,
    last_active_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_session_user_session (user_id, session_id),
    KEY idx_conversation_session_user_active (user_id, last_active_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Durable AI conversation sessions';

INSERT INTO conversation_session (
    user_id,
    session_id,
    title,
    last_active_time,
    create_time,
    update_time
)
SELECT
    ch.user_id,
    ch.session_id,
    (
        SELECT SUBSTRING(TRIM(first_user.content), 1, 80)
        FROM conversation_history first_user
        WHERE first_user.user_id = ch.user_id
          AND first_user.session_id = ch.session_id
          AND first_user.role = 'user'
          AND first_user.content IS NOT NULL
          AND TRIM(first_user.content) != ''
        ORDER BY first_user.create_time ASC, first_user.id ASC
        LIMIT 1
    ),
    COALESCE(MAX(ch.create_time), CURRENT_TIMESTAMP),
    COALESCE(MIN(ch.create_time), CURRENT_TIMESTAMP),
    COALESCE(MAX(ch.create_time), CURRENT_TIMESTAMP)
FROM conversation_history ch
GROUP BY ch.user_id, ch.session_id
ON DUPLICATE KEY UPDATE
    title = COALESCE(NULLIF(conversation_session.title, ''), VALUES(title)),
    last_active_time = GREATEST(conversation_session.last_active_time, VALUES(last_active_time)),
    update_time = GREATEST(conversation_session.update_time, VALUES(update_time));
