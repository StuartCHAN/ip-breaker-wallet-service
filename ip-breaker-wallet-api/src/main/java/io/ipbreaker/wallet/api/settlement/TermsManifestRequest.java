package io.ipbreaker.wallet.api.settlement;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigInteger;

public record TermsManifestRequest(
        @Min(1) int schemaVersion,
        @Min(1) long termsVersion,
        @NotNull BigInteger assetId,
        @NotBlank @Pattern(regexp = "0x[0-9a-fA-F]{40}") String licensor,
        @NotBlank @Pattern(regexp = "0x[0-9a-fA-F]{40}") String licensee,
        @NotBlank @Pattern(regexp = "0x[0-9a-fA-F]{40}") String payer,
        @NotBlank @Pattern(regexp = "0x[0-9a-fA-F]{40}") String payee,
        @NotBlank String currency,
        @NotNull @Positive BigInteger amount) {
}
