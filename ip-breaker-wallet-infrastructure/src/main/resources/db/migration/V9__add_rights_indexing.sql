ALTER TABLE chain_transaction ADD COLUMN input_data LONGTEXT NULL;

CREATE TABLE chain_contract (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    network_id BIGINT UNSIGNED NOT NULL,
    contract_type VARCHAR(32) NOT NULL,
    contract_address CHAR(42) NOT NULL,
    abi_version VARCHAR(64) NOT NULL,
    deployment_block BIGINT UNSIGNED NOT NULL,
    deployment_tx_hash CHAR(66) NOT NULL,
    runtime_code_hash CHAR(66) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_chain_contract_address (network_id, contract_address),
    KEY idx_chain_contract_type_status (network_id, contract_type, status),
    CONSTRAINT fk_chain_contract_network FOREIGN KEY (network_id) REFERENCES chain_network (id),
    CONSTRAINT chk_chain_contract_type CHECK (contract_type IN
        ('IP_ASSET_REGISTRY', 'EVIDENCE_REGISTRY', 'LICENSE_ESCROW')),
    CONSTRAINT chk_chain_contract_status CHECK (status IN
        ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED')),
    CONSTRAINT chk_chain_contract_address_lower CHECK (contract_address = LOWER(contract_address))
);

CREATE TABLE chain_domain_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    network_id BIGINT UNSIGNED NOT NULL,
    contract_id BIGINT UNSIGNED NOT NULL,
    contract_address CHAR(42) NOT NULL,
    block_number BIGINT UNSIGNED NOT NULL,
    block_hash CHAR(66) NOT NULL,
    block_timestamp TIMESTAMP(6) NOT NULL,
    tx_hash CHAR(66) NOT NULL,
    transaction_index INT UNSIGNED NOT NULL,
    log_index INT UNSIGNED NOT NULL,
    topic0 CHAR(66) NOT NULL,
    raw_topics_json JSON NOT NULL,
    raw_data LONGTEXT NOT NULL,
    event_name VARCHAR(96) NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    aggregate_type VARCHAR(48) NULL,
    aggregate_id DECIMAL(78,0) NULL,
    related_asset_id DECIMAL(78,0) NULL,
    payload_json JSON NOT NULL,
    payload_hash CHAR(66) NOT NULL,
    decoder_version VARCHAR(64) NOT NULL,
    canonical_status VARCHAR(16) NOT NULL,
    projection_error_code VARCHAR(64) NULL,
    observed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    orphaned_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chain_domain_log (network_id, contract_address, tx_hash, log_index),
    KEY idx_domain_event_order (network_id, block_number, transaction_index, log_index),
    KEY idx_domain_aggregate
        (network_id, aggregate_type, aggregate_id, canonical_status, block_number, log_index),
    KEY idx_domain_asset_timeline
        (network_id, related_asset_id, canonical_status, block_number, log_index),
    CONSTRAINT fk_domain_event_network FOREIGN KEY (network_id) REFERENCES chain_network (id),
    CONSTRAINT fk_domain_event_contract FOREIGN KEY (contract_id) REFERENCES chain_contract (id),
    CONSTRAINT chk_domain_canonical CHECK (canonical_status IN ('CANONICAL', 'ORPHANED'))
);

CREATE TABLE chain_unknown_event (
    domain_event_id BIGINT UNSIGNED NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    first_seen_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at TIMESTAMP(6) NULL,
    resolution_note VARCHAR(255) NULL,
    PRIMARY KEY (domain_event_id),
    CONSTRAINT fk_unknown_domain_event FOREIGN KEY (domain_event_id) REFERENCES chain_domain_event (id)
);

CREATE TABLE ip_asset_projection (
    network_id BIGINT UNSIGNED NOT NULL,
    registry_address CHAR(42) NOT NULL,
    asset_id DECIMAL(78,0) NOT NULL,
    owner_address CHAR(42) NOT NULL,
    title VARCHAR(512) NOT NULL,
    asset_type VARCHAR(128) NOT NULL,
    jurisdiction VARCHAR(128) NOT NULL,
    document_hash CHAR(66) NOT NULL,
    metadata_uri TEXT NOT NULL,
    asset_status VARCHAR(16) NOT NULL,
    registered_at TIMESTAMP(6) NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    source_event_id BIGINT UNSIGNED NOT NULL,
    effective_block_number BIGINT UNSIGNED NOT NULL,
    effective_block_hash CHAR(66) NOT NULL,
    effective_log_index INT UNSIGNED NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (network_id, registry_address, asset_id),
    CONSTRAINT fk_asset_projection_event FOREIGN KEY (source_event_id) REFERENCES chain_domain_event (id)
);

CREATE TABLE evidence_projection (
    network_id BIGINT UNSIGNED NOT NULL,
    registry_address CHAR(42) NOT NULL,
    evidence_id DECIMAL(78,0) NOT NULL,
    asset_id DECIMAL(78,0) NOT NULL,
    evidence_type VARCHAR(128) NOT NULL,
    evidence_hash CHAR(66) NOT NULL,
    evidence_uri TEXT NOT NULL,
    attestation_uid CHAR(66) NOT NULL,
    submitted_by CHAR(42) NOT NULL,
    submitted_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reviewed_by CHAR(42) NULL,
    reviewed_at TIMESTAMP(6) NULL,
    version BIGINT UNSIGNED NOT NULL,
    source_event_id BIGINT UNSIGNED NOT NULL,
    effective_block_number BIGINT UNSIGNED NOT NULL,
    effective_block_hash CHAR(66) NOT NULL,
    effective_log_index INT UNSIGNED NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (network_id, registry_address, evidence_id),
    KEY idx_evidence_asset (network_id, asset_id, status),
    CONSTRAINT fk_evidence_projection_event FOREIGN KEY (source_event_id) REFERENCES chain_domain_event (id)
);

CREATE TABLE license_agreement_projection (
    network_id BIGINT UNSIGNED NOT NULL,
    escrow_address CHAR(42) NOT NULL,
    agreement_id DECIMAL(78,0) NOT NULL,
    asset_id DECIMAL(78,0) NOT NULL,
    licensor CHAR(42) NOT NULL,
    licensee CHAR(42) NOT NULL,
    arbiter CHAR(42) NOT NULL,
    license_fee_raw DECIMAL(78,0) NOT NULL,
    escrowed_amount_raw DECIMAL(78,0) NOT NULL,
    terms_hash CHAR(66) NOT NULL,
    terms_uri TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    funded_at TIMESTAMP(6) NULL,
    released_to CHAR(42) NULL,
    released_amount_raw DECIMAL(78,0) NULL,
    dispute_raised_by CHAR(42) NULL,
    dispute_paid_to_licensor BOOLEAN NULL,
    dispute_resolved_amount_raw DECIMAL(78,0) NULL,
    version BIGINT UNSIGNED NOT NULL,
    source_event_id BIGINT UNSIGNED NOT NULL,
    effective_block_number BIGINT UNSIGNED NOT NULL,
    effective_block_hash CHAR(66) NOT NULL,
    effective_log_index INT UNSIGNED NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (network_id, escrow_address, agreement_id),
    KEY idx_agreement_asset (network_id, asset_id, status),
    CONSTRAINT fk_agreement_projection_event FOREIGN KEY (source_event_id) REFERENCES chain_domain_event (id)
);

CREATE TABLE projection_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    network_id BIGINT UNSIGNED NOT NULL,
    projection_type VARCHAR(32) NOT NULL,
    contract_address CHAR(42) NOT NULL,
    aggregate_id DECIMAL(78,0) NOT NULL,
    projection_version BIGINT UNSIGNED NOT NULL,
    snapshot_json JSON NOT NULL,
    valid_from_event_id BIGINT UNSIGNED NOT NULL,
    canonical_status VARCHAR(16) NOT NULL,
    invalidated_by_rebuild_id BIGINT UNSIGNED NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_projection_history_version
        (network_id, projection_type, contract_address, aggregate_id, projection_version),
    KEY idx_projection_history_event (valid_from_event_id),
    CONSTRAINT fk_projection_history_event FOREIGN KEY (valid_from_event_id) REFERENCES chain_domain_event (id),
    CONSTRAINT chk_projection_history_status CHECK (canonical_status IN ('CANONICAL', 'ORPHANED'))
);

CREATE TABLE projection_rebuild_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    network_id BIGINT UNSIGNED NOT NULL,
    reason VARCHAR(24) NOT NULL,
    ancestor_block_number BIGINT UNSIGNED NULL,
    from_block_number BIGINT UNSIGNED NOT NULL,
    to_block_number BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL,
    affected_event_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    affected_asset_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    affected_evidence_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    affected_agreement_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_projection_rebuild_status (network_id, status),
    CONSTRAINT chk_projection_rebuild_reason CHECK
        (reason IN ('REORG', 'BACKFILL', 'MANUAL_REPAIR', 'ABI_REDECODE')),
    CONSTRAINT chk_projection_rebuild_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE ip_contract_backfill_cursor (
    network_id BIGINT UNSIGNED NOT NULL,
    contract_id BIGINT UNSIGNED NOT NULL,
    next_block BIGINT UNSIGNED NOT NULL,
    target_safe_block BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_until TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (network_id, contract_id),
    CONSTRAINT fk_backfill_contract FOREIGN KEY (contract_id) REFERENCES chain_contract (id),
    CONSTRAINT chk_backfill_status CHECK (status IN ('PENDING', 'RUNNING', 'CAUGHT_UP', 'FAILED'))
);
