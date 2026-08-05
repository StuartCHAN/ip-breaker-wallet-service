package io.ipbreaker.wallet.application.settlement;

import java.math.BigInteger;

public record EligibilityFacts(
        String assetOwner,
        String assetStatus,
        String licensor,
        String licensee,
        String manifestHash,
        String chainTermsHash,
        String agreementStatus,
        BigInteger assetId,
        BigInteger agreementAssetId,
        String payer,
        String payee,
        String currency,
        BigInteger amount,
        BigInteger licenseFee,
        String paymentPayer,
        BigInteger paymentAmount) {
}
