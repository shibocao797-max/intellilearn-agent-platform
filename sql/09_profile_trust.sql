-- Learning-profile trust model. Safe to execute repeatedly on an existing database.

CREATE TABLE IF NOT EXISTS learner_profile_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    version INT NOT NULL,
    profile_json LONGTEXT NOT NULL,
    change_summary VARCHAR(500) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_version (profile_id, version),
    KEY idx_snapshot_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Learning profile version snapshots';

CREATE TABLE IF NOT EXISTS learner_profile_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    profile_version INT NOT NULL,
    dimension_key VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref VARCHAR(128) DEFAULT NULL,
    excerpt VARCHAR(500) NOT NULL,
    confidence DECIMAL(4,3) DEFAULT NULL,
    evidence_status VARCHAR(32) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_evidence_user_time (user_id, create_time),
    KEY idx_evidence_profile_dimension (profile_id, dimension_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Traceable learning profile evidence';
