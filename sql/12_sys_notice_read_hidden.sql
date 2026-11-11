-- 用户级通知隐藏：只影响当前用户的顶部最近通知，不删除 sys_notice 公告本体。
-- The base schema already contains this column; older existing databases may not.
SET @edu_add_notice_hidden_time = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sys_notice_read'
       AND COLUMN_NAME = 'deleted_time') = 0,
    'ALTER TABLE sys_notice_read ADD COLUMN deleted_time datetime DEFAULT NULL COMMENT ''当前用户隐藏时间'' AFTER read_time',
    'SELECT 1'
);
PREPARE edu_notice_hidden_stmt FROM @edu_add_notice_hidden_time;
EXECUTE edu_notice_hidden_stmt;
DEALLOCATE PREPARE edu_notice_hidden_stmt;
