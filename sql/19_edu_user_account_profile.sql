-- Account-owned profile and preference data for the Vue3 education frontend.
-- The unique user_id key guarantees one profile row per account.
create table if not exists `edu_user_account_profile` (
  `id` bigint(20) not null auto_increment comment 'primary key',
  `user_id` bigint(20) not null comment 'authenticated user id',
  `bio` varchar(240) not null default '' comment 'personal introduction, max 240 characters',
  `preferences_json` longtext not null comment 'validated account preferences JSON',
  `create_time` datetime default current_timestamp comment 'created time',
  `update_time` datetime default current_timestamp on update current_timestamp comment 'updated time',
  primary key (`id`),
  unique key `uk_edu_user_account_profile_user_id` (`user_id`)
) engine=innodb default charset=utf8mb4 comment='account-owned profile and preferences';
