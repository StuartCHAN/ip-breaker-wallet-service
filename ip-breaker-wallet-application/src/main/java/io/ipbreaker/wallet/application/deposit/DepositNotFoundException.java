package io.ipbreaker.wallet.application.deposit;

public class DepositNotFoundException extends RuntimeException {
    public DepositNotFoundException(long depositId) {
        super("Deposit not found: " + depositId);
    }
}
