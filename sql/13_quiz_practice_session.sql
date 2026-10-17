-- Resumable learner practice sessions. Run once after the existing quiz tables.

CREATE TABLE IF NOT EXISTS quiz_practice_session (
    id varchar(64) NOT NULL,
    user_id bigint NOT NULL,
    source varchar(32) NOT NULL COMMENT 'BANK, WRONG, AI_CHAT, RECOMMENDED',
    status varchar(20) NOT NULL COMMENT 'IN_PROGRESS, COMPLETED, ABANDONED',
    total_count int NOT NULL DEFAULT 0,
    submitted_count int NOT NULL DEFAULT 0,
    correct_count int NOT NULL DEFAULT 0,
    current_position int NOT NULL DEFAULT 1,
    started_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at datetime DEFAULT NULL,
    active_user_id bigint GENERATED ALWAYS AS (CASE WHEN status = 'IN_PROGRESS' THEN user_id ELSE NULL END) STORED,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_quiz_practice_session_user_status_time (user_id, status, last_active_at),
    UNIQUE KEY uk_quiz_practice_session_one_active (active_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Resumable learner practice sessions';

CREATE TABLE IF NOT EXISTS quiz_practice_item (
    id bigint NOT NULL AUTO_INCREMENT,
    session_id varchar(64) NOT NULL,
    user_id bigint NOT NULL,
    question_id bigint NOT NULL,
    position_index int NOT NULL,
    state varchar(20) NOT NULL COMMENT 'UNANSWERED, DRAFT, SUBMITTED',
    draft_answer text DEFAULT NULL,
    is_flagged tinyint(1) NOT NULL DEFAULT 0,
    submitted_answer text DEFAULT NULL,
    is_correct tinyint(1) DEFAULT NULL,
    pending_review tinyint(1) NOT NULL DEFAULT 0,
    score decimal(5,2) DEFAULT NULL,
    time_spent_sec int NOT NULL DEFAULT 0,
    quiz_record_id bigint DEFAULT NULL,
    idempotency_key varchar(64) DEFAULT NULL,
    submitted_at datetime DEFAULT NULL,
    create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_quiz_practice_item_session_question (session_id, question_id),
    KEY idx_quiz_practice_item_owner_session_position (user_id, session_id, position_index),
    KEY idx_quiz_practice_item_record (quiz_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-question learner practice state';

CREATE INDEX idx_quiz_record_user_question_latest
    ON quiz_record (user_id, question_id, id);
