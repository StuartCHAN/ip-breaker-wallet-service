package io.ipbreaker.wallet.common.error;

public enum ErrorCode {
    INVALID_REQUEST("WALLET-400-001", "Invalid request"),
    DEPOSIT_ADDRESS_NOT_FOUND("WALLET-404-001", "Deposit address not found"),
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
