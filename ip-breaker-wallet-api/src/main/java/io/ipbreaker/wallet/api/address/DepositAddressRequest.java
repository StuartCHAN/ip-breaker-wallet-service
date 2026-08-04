package io.ipbreaker.wallet.api.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DepositAddressRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9_]{2,32}$")
        String networkCode) {
}
