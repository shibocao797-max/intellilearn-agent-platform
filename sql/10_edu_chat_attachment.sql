-- storage_path is server-only; never expose it as an attachment URL.
CREATE TABLE IF NOT EXISTS `edu_chat_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `original_name` varchar(200) NOT NULL,
  `storage_path` varchar(1000) NOT NULL,
  `file_size` bigint NOT NULL DEFAULT 0,
  `mime_type` varchar(100) NOT NULL DEFAULT '',
  `extension` varchar(20) NOT NULL DEFAULT '',
  `content_status` varchar(32) NOT NULL,
  `extracted_text` mediumtext NULL,
  `error_message` varchar(500) NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_edu_chat_attachment_owner_session` (`user_id`,`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI chat attachment metadata';
