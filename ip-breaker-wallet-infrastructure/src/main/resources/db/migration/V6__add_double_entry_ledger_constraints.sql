ALTER TABLE ledger_account
    ADD CONSTRAINT chk_ledger_owner_type CHECK (owner_type IN ('SYSTEM', 'USER')),
    ADD CONSTRAINT chk_ledger_account_type CHECK (account_type IN ('ASSET', 'LIABILITY')),
    ADD CONSTRAINT chk_ledger_account_status CHECK (status = 'ACTIVE');

ALTER TABLE ledger_transaction
    ADD CONSTRAINT chk_ledger_transaction_status CHECK (status = 'POSTED');

ALTER TABLE ledger_entry
    ADD CONSTRAINT chk_ledger_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    ADD CONSTRAINT chk_ledger_entry_positive CHECK (amount_raw > 0),
    ADD CONSTRAINT uk_ledger_entry UNIQUE
        (ledger_transaction_id, ledger_account_id, direction);

ALTER TABLE account_balance
    ADD CONSTRAINT chk_account_balance_nonnegative CHECK (
        available_amount_raw >= 0 AND pending_amount_raw >= 0
    );

CREATE INDEX idx_deposit_crediting
    ON deposit (status, credited_ledger_tx_id, id);
