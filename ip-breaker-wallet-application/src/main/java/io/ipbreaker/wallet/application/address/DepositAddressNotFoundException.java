package io.ipbreaker.wallet.application.address;

public final class DepositAddressNotFoundException extends RuntimeException {
    public DepositAddressNotFoundException(String networkCode, String userId) {
        super("No deposit address for user " + userId + " on network " + networkCode);
    }
}
