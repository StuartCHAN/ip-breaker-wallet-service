CREATE TABLE reconciliation_difference (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    check_type VARCHAR(32) NOT NULL,
    network_code VARCHAR(32) NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_key VARCHAR(128) NOT NULL,
    expected_amount_raw DECIMAL(78,0) NOT NULL,
    actual_amount_raw DECIMAL(78,0) NOT NULL,
    details VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    occurrence_count BIGINT UNSIGNED NOT NULL DEFAULT 1,
    first_detected_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_detected_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reconciliation_subject
        (check_type, network_code, asset_code, subject_type, subject_key),
    KEY idx_reconciliation_status (status, last_detected_at)
);
