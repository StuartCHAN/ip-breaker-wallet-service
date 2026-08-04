package io.ipbreaker.wallet.application.address;

public final class AddressPoolExhaustedException extends RuntimeException {
    public AddressPoolExhaustedException(String networkCode) {
        super("No deposit address is available for network " + networkCode);
    }
}
