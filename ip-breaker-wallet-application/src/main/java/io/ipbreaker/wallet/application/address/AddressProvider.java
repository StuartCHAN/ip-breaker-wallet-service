package io.ipbreaker.wallet.application.address;

public interface AddressProvider {
    ProvidedAddress nextAddress(long networkId);

    record ProvidedAddress(String normalizedAddress, Long derivationIndex) {
    }
}
