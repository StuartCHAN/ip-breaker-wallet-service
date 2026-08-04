package io.ipbreaker.wallet.api.address;

import io.ipbreaker.wallet.application.address.DepositAddressService;
import io.ipbreaker.wallet.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/deposit-addresses")
public class DepositAddressController {
    private final DepositAddressService service;

    public DepositAddressController(DepositAddressService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<DepositAddressResponse> allocate(
            @PathVariable @NotBlank @Size(max = 64) String userId,
            @Valid @RequestBody DepositAddressRequest request) {
        return ApiResponse.success(DepositAddressResponse.from(
                service.getOrAllocate(request.networkCode(), userId)));
    }

    @GetMapping
    public ApiResponse<DepositAddressResponse> get(
            @PathVariable @NotBlank @Size(max = 64) String userId,
            @RequestParam @Pattern(regexp = "^[A-Z0-9_]{2,32}$") String networkCode) {
        return ApiResponse.success(DepositAddressResponse.from(service.get(networkCode, userId)));
    }
}
