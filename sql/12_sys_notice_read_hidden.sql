-- 用户级通知隐藏：只影响当前用户的顶部最近通知，不删除 sys_notice 公告本体。
ALTER TABLE sys_notice_read
    ADD COLUMN deleted_time datetime DEFAULT NULL COMMENT '当前用户隐藏时间' AFTER read_time;
