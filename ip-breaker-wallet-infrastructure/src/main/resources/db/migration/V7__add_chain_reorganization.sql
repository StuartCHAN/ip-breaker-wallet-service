ALTER TABLE chain_block DROP INDEX uk_block_height;

ALTER TABLE chain_block
    ADD COLUMN canonical_height BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE WHEN status <> 'ORPHANED' THEN block_number ELSE NULL END
        ) STORED,
    ADD UNIQUE KEY uk_canonical_block_height (network_id, canonical_height),
    ADD CONSTRAINT chk_chain_block_status CHECK (status IN ('SAFE', 'ORPHANED'));

ALTER TABLE chain_transaction DROP INDEX uk_chain_tx_hash;
ALTER TABLE chain_transaction
    ADD UNIQUE KEY uk_chain_tx_in_block (block_id, tx_hash);

ALTER TABLE chain_receipt DROP INDEX uk_receipt_tx_hash;
ALTER TABLE chain_receipt
    ADD UNIQUE KEY uk_receipt_transaction (transaction_id);

CREATE INDEX idx_deposit_reorganization
    ON deposit (block_id, status, credited_ledger_tx_id);

ALTER TABLE account_balance DROP CHECK chk_account_balance_nonnegative;
