INSERT INTO chain_network
    (network_code, chain_id, native_symbol, required_confirmations, scan_start_block, status)
VALUES ('SEPOLIA', 11155111, 'ETH', 12, 0, 'ACTIVE');

INSERT INTO asset
    (network_id, asset_code, asset_type, contract_address, symbol, decimals, deposit_enabled)
SELECT id, 'ETH_SEPOLIA', 'NATIVE', NULL, 'ETH', 18, TRUE
FROM chain_network WHERE network_code = 'SEPOLIA';

