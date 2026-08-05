package io.ipbreaker.wallet.common.error;

public enum ErrorCode {
    INVALID_REQUEST("WALLET-400-001", "Invalid request"),
    DEPOSIT_ADDRESS_NOT_FOUND("WALLET-404-001", "Deposit address not found"),
    DEPOSIT_NOT_FOUND("WALLET-404-002", "Deposit not found"),
    IP_ASSET_NOT_FOUND("RIGHTS-404-001", "IP asset not found"),
    LICENSE_AGREEMENT_NOT_FOUND("RIGHTS-404-002", "License agreement not found"),
    PAYMENT_OBLIGATION_NOT_FOUND("SETTLEMENT-404-001", "Payment obligation not found"),
    SETTLEMENT_RECORD_NOT_FOUND("SETTLEMENT-404-002", "Settlement record not found"),
    TERMS_MANIFEST_CONFLICT("SETTLEMENT-409-001", "Terms manifest conflicts with on-chain terms"),
    SETTLEMENT_NOT_POSTABLE("SETTLEMENT-409-002", "Settlement is not eligible and clear for posting"),
    PROJECTION_REBUILD_IN_PROGRESS("RIGHTS-409-001", "Projection rebuild in progress"),
    INDEXER_NOT_READY("RIGHTS-503-001", "Rights indexer is not ready"),
    ADDRESS_POOL_EXHAUSTED("WALLET-503-001", "Deposit address pool exhausted"),
    INTERNAL_ERROR("WALLET-500-001", "Internal server error");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
