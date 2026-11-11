-- Preserve the question shown at practice-session creation time.
-- Fresh databases create this column in 13; this adds it to older databases only.
SET @edu_add_practice_question_snapshot = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'quiz_practice_item'
       AND COLUMN_NAME = 'question_snapshot_json') = 0,
    'ALTER TABLE quiz_practice_item ADD COLUMN question_snapshot_json MEDIUMTEXT DEFAULT NULL AFTER question_id',
    'SELECT 1'
);
PREPARE edu_practice_snapshot_stmt FROM @edu_add_practice_question_snapshot;
EXECUTE edu_practice_snapshot_stmt;
DEALLOCATE PREPARE edu_practice_snapshot_stmt;
