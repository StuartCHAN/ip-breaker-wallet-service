CREATE TABLE chain_network (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    network_code VARCHAR(32) NOT NULL,
    chain_id BIGINT UNSIGNED NOT NULL,
    native_symbol VARCHAR(16) NOT NULL,
    required_confirmations INT UNSIGNED NOT NULL,
    scan_start_block BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_chain_network_code (network_code), UNIQUE KEY uk_chain_id (chain_id)
);

CREATE TABLE asset (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, network_id BIGINT UNSIGNED NOT NULL,
    asset_code VARCHAR(32) NOT NULL, asset_type VARCHAR(16) NOT NULL,
    contract_address VARCHAR(42) NULL, symbol VARCHAR(16) NOT NULL, decimals TINYINT UNSIGNED NOT NULL,
    deposit_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_asset_code (network_id, asset_code),
    UNIQUE KEY uk_asset_contract (network_id, contract_address),
    CONSTRAINT fk_asset_network FOREIGN KEY (network_id) REFERENCES chain_network (id)
);

CREATE TABLE wallet_address (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, network_id BIGINT UNSIGNED NOT NULL,
    user_id VARCHAR(64) NULL, address VARCHAR(42) NOT NULL, derivation_index BIGINT UNSIGNED NULL,
    address_type VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL, assigned_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_wallet_address (network_id, address),
    UNIQUE KEY uk_user_address_type (network_id, user_id, address_type),
    CONSTRAINT fk_wallet_address_network FOREIGN KEY (network_id) REFERENCES chain_network (id)
);

CREATE TABLE chain_block (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, network_id BIGINT UNSIGNED NOT NULL,
    block_number BIGINT UNSIGNED NOT NULL, block_hash CHAR(66) NOT NULL, parent_hash CHAR(66) NOT NULL,
    block_timestamp TIMESTAMP(6) NOT NULL, status VARCHAR(16) NOT NULL,
    scanned_at TIMESTAMP(6) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_block_hash (network_id, block_hash),
    KEY idx_block_height_status (network_id, block_number, status),
    CONSTRAINT fk_block_network FOREIGN KEY (network_id) REFERENCES chain_network (id)
);

CREATE TABLE chain_transaction (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, network_id BIGINT UNSIGNED NOT NULL,
    block_id BIGINT UNSIGNED NOT NULL, tx_hash CHAR(66) NOT NULL, from_address VARCHAR(42) NOT NULL,
    to_address VARCHAR(42) NULL, nonce DECIMAL(78,0) NOT NULL, value_raw DECIMAL(78,0) NOT NULL,
    tx_status VARCHAR(16) NOT NULL, transaction_index INT UNSIGNED NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_chain_tx_hash (network_id, tx_hash), KEY idx_tx_block (block_id),
    CONSTRAINT fk_tx_network FOREIGN KEY (network_id) REFERENCES chain_network (id),
    CONSTRAINT fk_tx_block FOREIGN KEY (block_id) REFERENCES chain_block (id)
);

CREATE TABLE ledger_account (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, owner_type VARCHAR(16) NOT NULL,
    owner_id VARCHAR(64) NOT NULL, asset_id BIGINT UNSIGNED NOT NULL, account_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_ledger_account (owner_type, owner_id, asset_id, account_type),
    CONSTRAINT fk_account_asset FOREIGN KEY (asset_id) REFERENCES asset (id)
);

CREATE TABLE ledger_transaction (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, business_type VARCHAR(32) NOT NULL,
    business_id BIGINT UNSIGNED NOT NULL, reference_no VARCHAR(64) NOT NULL, status VARCHAR(16) NOT NULL,
    description VARCHAR(255) NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_ledger_business (business_type, business_id),
    UNIQUE KEY uk_ledger_reference (reference_no)
);

CREATE TABLE deposit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, network_id BIGINT UNSIGNED NOT NULL,
    asset_id BIGINT UNSIGNED NOT NULL, user_id VARCHAR(64) NOT NULL,
    wallet_address_id BIGINT UNSIGNED NOT NULL, block_id BIGINT UNSIGNED NOT NULL,
    tx_hash CHAR(66) NOT NULL, log_index INT NOT NULL, from_address VARCHAR(42) NOT NULL,
    to_address VARCHAR(42) NOT NULL, amount_raw DECIMAL(78,0) NOT NULL,
    block_number BIGINT UNSIGNED NOT NULL, confirmations INT UNSIGNED NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL, credited_ledger_tx_id BIGINT UNSIGNED NULL,
    detected_at TIMESTAMP(6) NOT NULL, confirmed_at TIMESTAMP(6) NULL,
    credited_at TIMESTAMP(6) NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), UNIQUE KEY uk_deposit_event (network_id, tx_hash, asset_id, log_index),
    KEY idx_deposit_user (user_id, status), KEY idx_deposit_block (block_id),
    CONSTRAINT fk_deposit_network FOREIGN KEY (network_id) REFERENCES chain_network (id),
    CONSTRAINT fk_deposit_asset FOREIGN KEY (asset_id) REFERENCES asset (id),
    CONSTRAINT fk_deposit_address FOREIGN KEY (wallet_address_id) REFERENCES wallet_address (id),
    CONSTRAINT fk_deposit_block FOREIGN KEY (block_id) REFERENCES chain_block (id),
    CONSTRAINT fk_deposit_ledger_tx FOREIGN KEY (credited_ledger_tx_id) REFERENCES ledger_transaction (id)
);

CREATE TABLE ledger_entry (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, ledger_transaction_id BIGINT UNSIGNED NOT NULL,
    ledger_account_id BIGINT UNSIGNED NOT NULL, direction VARCHAR(8) NOT NULL,
    amount_raw DECIMAL(78,0) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), KEY idx_entry_transaction (ledger_transaction_id),
    KEY idx_entry_account (ledger_account_id),
    CONSTRAINT fk_entry_tx FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_transaction (id),
    CONSTRAINT fk_entry_account FOREIGN KEY (ledger_account_id) REFERENCES ledger_account (id)
);

CREATE TABLE account_balance (
    ledger_account_id BIGINT UNSIGNED NOT NULL, available_amount_raw DECIMAL(78,0) NOT NULL DEFAULT 0,
    pending_amount_raw DECIMAL(78,0) NOT NULL DEFAULT 0, version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (ledger_account_id),
    CONSTRAINT fk_balance_account FOREIGN KEY (ledger_account_id) REFERENCES ledger_account (id)
);

CREATE TABLE scan_cursor (
    network_id BIGINT UNSIGNED NOT NULL, last_scanned_block BIGINT UNSIGNED NOT NULL,
    last_scanned_hash CHAR(66) NOT NULL, version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (network_id),
    CONSTRAINT fk_cursor_network FOREIGN KEY (network_id) REFERENCES chain_network (id)
);

CREATE TABLE outbox_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL, event_type VARCHAR(64) NOT NULL, payload_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL, retry_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id), KEY idx_outbox_publish (status, created_at)
);

