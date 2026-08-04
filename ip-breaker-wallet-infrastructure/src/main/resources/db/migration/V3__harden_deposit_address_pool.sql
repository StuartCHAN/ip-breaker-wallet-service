ALTER TABLE wallet_address
    ADD CONSTRAINT chk_wallet_address_lowercase CHECK (address = LOWER(address)),
    ADD CONSTRAINT chk_wallet_address_format CHECK (address REGEXP '^0x[0-9a-f]{40}$'),
    ADD CONSTRAINT chk_wallet_address_assignment CHECK (
        (status = 'AVAILABLE' AND user_id IS NULL AND assigned_at IS NULL)
        OR (status = 'ASSIGNED' AND user_id IS NOT NULL AND assigned_at IS NOT NULL)
    );

CREATE INDEX idx_wallet_address_pool
    ON wallet_address (network_id, address_type, status, id);
