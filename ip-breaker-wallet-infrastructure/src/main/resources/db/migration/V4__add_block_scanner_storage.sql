ALTER TABLE chain_block
    ADD CONSTRAINT uk_block_height UNIQUE (network_id, block_number);

CREATE TABLE chain_receipt (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    network_id BIGINT UNSIGNED NOT NULL,
    transaction_id BIGINT UNSIGNED NOT NULL,
    tx_hash CHAR(66) NOT NULL,
    block_number BIGINT UNSIGNED NOT NULL,
    transaction_index INT UNSIGNED NOT NULL,
    success BOOLEAN NOT NULL,
    gas_used DECIMAL(78,0) NULL,
    contract_address VARCHAR(42) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_receipt_tx_hash (network_id, tx_hash),
    CONSTRAINT fk_receipt_network FOREIGN KEY (network_id) REFERENCES chain_network (id),
    CONSTRAINT fk_receipt_transaction FOREIGN KEY (transaction_id) REFERENCES chain_transaction (id)
);

ALTER TABLE scan_cursor
    ADD COLUMN lease_owner VARCHAR(128) NULL,
    ADD COLUMN lease_until TIMESTAMP(6) NULL;
