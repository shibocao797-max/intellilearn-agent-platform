CREATE TABLE IF NOT EXISTS `edu_action_plan` (
 `id` bigint NOT NULL AUTO_INCREMENT, `user_id` bigint NOT NULL, `title` varchar(200) NOT NULL, `summary` varchar(1000) NOT NULL,
 `status` varchar(20) NOT NULL COMMENT 'DRAFT ACTIVE ARCHIVED', `source_status` varchar(64) NOT NULL, `source_profile_version` varchar(32) DEFAULT NULL,
 `active_user_id` bigint GENERATED ALWAYS AS (CASE WHEN `status` = 'ACTIVE' THEN `user_id` ELSE NULL END) STORED,
 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (`id`), KEY `idx_edu_action_plan_user_status` (`user_id`,`status`), UNIQUE KEY `uk_edu_action_plan_one_active` (`active_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User-confirmed learning action plans';

CREATE TABLE IF NOT EXISTS `edu_action_plan_item` (
 `id` bigint NOT NULL AUTO_INCREMENT, `plan_id` bigint NOT NULL, `user_id` bigint NOT NULL, `sort_order` int NOT NULL, `title` varchar(200) NOT NULL, `description` varchar(2000) NOT NULL,
 `status` varchar(20) NOT NULL COMMENT 'PENDING IN_PROGRESS COMPLETED SKIPPED', `completed_at` datetime DEFAULT NULL,
 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (`id`), KEY `idx_edu_action_plan_item_owner` (`user_id`,`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Learning action plan items';
