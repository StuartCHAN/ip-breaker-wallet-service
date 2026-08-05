package io.ipbreaker.wallet.application.settlement;

public class SettlementNotPostableException extends RuntimeException {
    public SettlementNotPostableException(String message) {
        super(message);
    }
}
