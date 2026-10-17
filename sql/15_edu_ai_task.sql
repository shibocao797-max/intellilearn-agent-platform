CREATE TABLE IF NOT EXISTS `edu_ai_task` (
  `task_id` varchar(36) NOT NULL,
  `user_id` bigint NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `client_request_id` varchar(128) NOT NULL,
  `message` mediumtext NOT NULL,
  `attachment_ids_json` json NULL,
  `attachment_context` mediumtext NULL,
  `status` varchar(16) NOT NULL,
  `cancel_requested` tinyint(1) NOT NULL DEFAULT 0,
  `result_json` json NULL,
  `error_message` varchar(2000) NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `start_time` datetime NULL,
  `finish_time` datetime NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`task_id`),
  UNIQUE KEY `uk_edu_ai_task_owner_request` (`user_id`,`client_request_id`),
  KEY `idx_edu_ai_task_owner_status` (`user_id`,`status`,`create_time`),
  KEY `idx_edu_ai_task_owner_session` (`user_id`,`session_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Durable AI chat background task';

-- sequence is global, but is strictly increasing within every task and can be
-- used directly as the afterSeq polling cursor without a per-task lock.
CREATE TABLE IF NOT EXISTS `edu_ai_task_event` (
  `sequence` bigint NOT NULL AUTO_INCREMENT,
  `task_id` varchar(36) NOT NULL,
  `session_id` varchar(64) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `agent_key` varchar(64) NULL,
  `status` varchar(16) NOT NULL,
  `payload_json` json NOT NULL,
  `occurred_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`sequence`),
  KEY `idx_edu_ai_task_event_replay` (`task_id`,`sequence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Replayable AI task event stream';
