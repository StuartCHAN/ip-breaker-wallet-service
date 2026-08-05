ALTER TABLE asset
    ADD CONSTRAINT chk_asset_contract_lowercase CHECK (
        contract_address IS NULL OR contract_address = LOWER(contract_address)
    ),
    ADD CONSTRAINT chk_asset_contract_format CHECK (
        contract_address IS NULL OR contract_address REGEXP '^0x[0-9a-f]{40}$'
    );

CREATE INDEX idx_deposit_user_id ON deposit (user_id, id);
