CREATE TABLE settlement_proof_package (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    network_id BIGINT UNSIGNED NOT NULL,
    agreement_id DECIMAL(78,0) NOT NULL,
    obligation_id BIGINT UNSIGNED NOT NULL,
    settlement_record_id BIGINT UNSIGNED NULL,
    package_version VARCHAR(32) NOT NULL,
    hash_algorithm VARCHAR(16) NOT NULL,
    content_hash CHAR(66) NOT NULL,
    package_json JSON NOT NULL,
    generated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_proof_hash (network_id, agreement_id, content_hash),
    KEY idx_settlement_proof_latest (network_id, agreement_id, id),
    CONSTRAINT fk_proof_network FOREIGN KEY (network_id) REFERENCES chain_network (id),
    CONSTRAINT fk_proof_obligation FOREIGN KEY (obligation_id) REFERENCES payment_obligation (id),
    CONSTRAINT fk_proof_settlement FOREIGN KEY (settlement_record_id) REFERENCES settlement_record (id),
    CONSTRAINT chk_proof_hash_algorithm CHECK (hash_algorithm = 'SHA-256')
);

CREATE TABLE reconciliation_checkpoint (
    check_type VARCHAR(32) NOT NULL,
    network_code VARCHAR(32) NOT NULL,
    difference_count INT UNSIGNED NOT NULL,
    completed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (check_type, network_code),
    CONSTRAINT chk_reconciliation_checkpoint_type CHECK (
        check_type IN ('LEDGER_BALANCE', 'DEPOSIT_LEDGER', 'ONCHAIN_PLATFORM'))
);
