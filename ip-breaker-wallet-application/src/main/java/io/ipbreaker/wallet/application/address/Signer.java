package io.ipbreaker.wallet.application.address;

public interface Signer {
    byte[] sign(String keyReference, byte[] payload);
}
