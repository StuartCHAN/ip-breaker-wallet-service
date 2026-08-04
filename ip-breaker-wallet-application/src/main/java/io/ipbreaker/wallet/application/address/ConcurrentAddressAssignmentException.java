package io.ipbreaker.wallet.application.address;

public final class ConcurrentAddressAssignmentException extends RuntimeException {
    public ConcurrentAddressAssignmentException(Throwable cause) {
        super("Deposit address was assigned concurrently", cause);
    }
}
