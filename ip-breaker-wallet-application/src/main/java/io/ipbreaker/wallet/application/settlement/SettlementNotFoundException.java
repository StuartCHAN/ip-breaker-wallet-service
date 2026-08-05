package io.ipbreaker.wallet.application.settlement;

public class SettlementNotFoundException extends RuntimeException {
    public SettlementNotFoundException() {
        super("Payment obligation not found");
    }
}
