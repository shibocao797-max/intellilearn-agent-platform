-- Compatibility patch for older databases. New databases already have the column in 13.
SET @edu_add_practice_position = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'quiz_practice_session'
       AND COLUMN_NAME = 'current_position') = 0,
    'ALTER TABLE quiz_practice_session ADD COLUMN current_position int NOT NULL DEFAULT 1 AFTER correct_count',
    'SELECT 1'
);
PREPARE edu_practice_position_stmt FROM @edu_add_practice_position;
EXECUTE edu_practice_position_stmt;
DEALLOCATE PREPARE edu_practice_position_stmt;
