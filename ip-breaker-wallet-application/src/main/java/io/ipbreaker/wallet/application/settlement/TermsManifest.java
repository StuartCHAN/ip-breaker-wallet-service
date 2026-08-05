package io.ipbreaker.wallet.application.settlement;

import java.math.BigInteger;

public record TermsManifest(
        int schemaVersion,
        long termsVersion,
        BigInteger assetId,
        String licensor,
        String licensee,
        String payer,
        String payee,
        String currency,
        BigInteger amount) {
}
