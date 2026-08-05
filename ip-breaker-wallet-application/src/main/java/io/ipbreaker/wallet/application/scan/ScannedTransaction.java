package io.ipbreaker.wallet.application.scan;

import java.math.BigInteger;

public record ScannedTransaction(
        String hash,
        String fromAddress,
        String toAddress,
        BigInteger nonce,
        BigInteger value,
        int transactionIndex,
        ScannedReceipt receipt) {
}
