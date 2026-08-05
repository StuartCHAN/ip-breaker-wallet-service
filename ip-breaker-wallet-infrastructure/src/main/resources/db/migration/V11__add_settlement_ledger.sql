CREATE TABLE settlement_allocation_plan (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    obligation_id BIGINT UNSIGNED NOT NULL,
    eligibility_snapshot_id BIGINT UNSIGNED NOT NULL,
    currency_kind VARCHAR(16) NOT NULL,
    total_amount_raw DECIMAL(78,0) NOT NULL,
    policy_version VARCHAR(32) NOT NULL,
    plan_hash CHAR(66) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_allocation_snapshot (eligibility_snapshot_id),
    KEY idx_allocation_hash (plan_hash),
    CONSTRAINT fk_allocation_obligation FOREIGN KEY (obligation_id) REFERENCES payment_obligation (id),
    CONSTRAINT fk_allocation_snapshot FOREIGN KEY (eligibility_snapshot_id)
        REFERENCES settlement_eligibility_snapshot (id),
    CONSTRAINT chk_allocation_currency CHECK (currency_kind = 'NATIVE'),
    CONSTRAINT chk_allocation_total CHECK (total_amount_raw > 0)
);

CREATE TABLE settlement_allocation_line (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    allocation_plan_id BIGINT UNSIGNED NOT NULL,
    line_number INT UNSIGNED NOT NULL,
    recipient CHAR(42) NOT NULL,
    amount_raw DECIMAL(78,0) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_allocation_line (allocation_plan_id, line_number),
    UNIQUE KEY uk_allocation_recipient (allocation_plan_id, recipient),
    CONSTRAINT fk_allocation_line_plan FOREIGN KEY (allocation_plan_id)
        REFERENCES settlement_allocation_plan (id),
    CONSTRAINT chk_allocation_recipient_lower CHECK (recipient = LOWER(recipient)),
    CONSTRAINT chk_allocation_line_amount CHECK (amount_raw > 0)
);

CREATE TABLE settlement_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    obligation_id BIGINT UNSIGNED NOT NULL,
    allocation_plan_id BIGINT UNSIGNED NOT NULL,
    settlement_status VARCHAR(16) NOT NULL,
    original_settlement_id BIGINT UNSIGNED NULL,
    reversed_by_settlement_id BIGINT UNSIGNED NULL,
    restored_from_reversal_id BIGINT UNSIGNED NULL,
    ledger_transaction_id BIGINT UNSIGNED NOT NULL,
    trigger_event_id BIGINT UNSIGNED NULL,
    safe_block_number BIGINT UNSIGNED NOT NULL,
    safe_block_hash CHAR(66) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_journal (ledger_transaction_id),
    UNIQUE KEY uk_settlement_original_status (original_settlement_id, settlement_status),
    KEY idx_settlement_obligation (obligation_id, id),
    CONSTRAINT fk_settlement_obligation FOREIGN KEY (obligation_id) REFERENCES payment_obligation (id),
    CONSTRAINT fk_settlement_plan FOREIGN KEY (allocation_plan_id) REFERENCES settlement_allocation_plan (id),
    CONSTRAINT fk_settlement_original FOREIGN KEY (original_settlement_id) REFERENCES settlement_record (id),
    CONSTRAINT fk_settlement_reversed_by FOREIGN KEY (reversed_by_settlement_id) REFERENCES settlement_record (id),
    CONSTRAINT fk_settlement_restored_from FOREIGN KEY (restored_from_reversal_id) REFERENCES settlement_record (id),
    CONSTRAINT fk_settlement_ledger FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_transaction (id),
    CONSTRAINT fk_settlement_trigger FOREIGN KEY (trigger_event_id) REFERENCES chain_domain_event (id),
    CONSTRAINT chk_settlement_status CHECK (settlement_status IN ('SETTLED', 'REVERSED', 'RESTORED'))
);

ALTER TABLE payment_obligation
    DROP CHECK chk_obligation_settlement,
    ADD CONSTRAINT chk_obligation_settlement CHECK (
        settlement_status IN ('PENDING', 'ELIGIBLE', 'SETTLED', 'REVERSED', 'RESTORED'));
