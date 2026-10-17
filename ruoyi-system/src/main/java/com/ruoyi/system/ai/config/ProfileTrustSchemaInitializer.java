package com.ruoyi.system.ai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Ensures profile trust tables exist for both fresh and already-running databases. */
@Component
public class ProfileTrustSchemaInitializer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS learner_profile_snapshot (
                    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, profile_id BIGINT NOT NULL,
                    version INT NOT NULL, profile_json LONGTEXT NOT NULL, change_summary VARCHAR(500) NOT NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id),
                    UNIQUE KEY uk_profile_version (profile_id, version), KEY idx_snapshot_user_time (user_id, create_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS learner_profile_evidence (
                    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, profile_id BIGINT NOT NULL,
                    profile_version INT NOT NULL, dimension_key VARCHAR(64) NOT NULL, source_type VARCHAR(32) NOT NULL,
                    source_ref VARCHAR(128) DEFAULT NULL, excerpt VARCHAR(500) NOT NULL, confidence DECIMAL(4,3) DEFAULT NULL,
                    evidence_status VARCHAR(32) NOT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id), KEY idx_evidence_user_time (user_id, create_time),
                    KEY idx_evidence_profile_dimension (profile_id, dimension_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS learner_profile_confirmation (
                    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL, profile_id BIGINT NOT NULL,
                    profile_version INT NOT NULL, dimension_key VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL,
                    correction_value_json MEDIUMTEXT DEFAULT NULL, reject_reason VARCHAR(500) DEFAULT NULL,
                    confirmed_by BIGINT DEFAULT NULL, confirmed_at DATETIME DEFAULT NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_profile_confirmation_dimension (user_id, profile_id, profile_version, dimension_key),
                    KEY idx_profile_confirmation_user (user_id, create_time),
                    KEY idx_profile_confirmation_profile (profile_id, profile_version)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }
}
