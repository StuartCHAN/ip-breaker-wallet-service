package io.ipbreaker.wallet.application.audit;

public class SettlementAuditNotFoundException extends RuntimeException {
    public SettlementAuditNotFoundException() {
        super("Settlement audit trail not found");
    }
}
