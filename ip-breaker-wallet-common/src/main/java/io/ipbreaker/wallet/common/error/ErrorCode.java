package io.ipbreaker.wallet.common.error;

public enum ErrorCode {
    INVALID_REQUEST("WALLET-400-001", "Invalid request"),
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
