-- User workspace persistence for the Vue3 frontend.
create table if not exists `edu_user_workspace` (
  `id` bigint(20) not null auto_increment comment 'primary key',
  `user_id` bigint(20) not null comment 'user id',
  `workspace_json` longtext not null comment 'workspace json',
  `create_time` datetime default current_timestamp comment 'created time',
  `update_time` datetime default current_timestamp on update current_timestamp comment 'updated time',
  primary key (`id`),
  unique key `uk_edu_user_workspace_user_id` (`user_id`)
) engine=innodb default charset=utf8mb4 comment='user workspace table';

update `sys_config`
set `config_value` = 'true'
where `config_key` = 'sys.account.registerUser';
