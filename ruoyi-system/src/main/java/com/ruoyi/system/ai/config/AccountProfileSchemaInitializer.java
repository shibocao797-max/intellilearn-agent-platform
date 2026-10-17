package com.ruoyi.system.ai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures account profile storage exists for fresh and existing databases. */
@Component
public class AccountProfileSchemaInitializer
{
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureTable()
    {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS edu_user_account_profile (
                id BIGINT NOT NULL AUTO_INCREMENT,
                user_id BIGINT NOT NULL,
                bio VARCHAR(240) NOT NULL DEFAULT '',
                preferences_json LONGTEXT NOT NULL,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (id),
                UNIQUE KEY uk_edu_user_account_profile_user_id (user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
    }
}
