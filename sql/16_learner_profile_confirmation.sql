-- Current profile-dimension confirmations. Historical evidence remains immutable.
CREATE TABLE IF NOT EXISTS learner_profile_confirmation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    profile_version INT NOT NULL,
    dimension_key VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    correction_value_json MEDIUMTEXT DEFAULT NULL,
    reject_reason VARCHAR(500) DEFAULT NULL,
    confirmed_by BIGINT DEFAULT NULL,
    confirmed_at DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_confirmation_dimension (user_id, profile_id, profile_version, dimension_key),
    KEY idx_profile_confirmation_user (user_id, create_time),
    KEY idx_profile_confirmation_profile (profile_id, profile_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Current learner profile dimension confirmations';
