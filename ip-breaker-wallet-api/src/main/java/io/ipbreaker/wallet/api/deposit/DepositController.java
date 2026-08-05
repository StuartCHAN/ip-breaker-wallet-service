package io.ipbreaker.wallet.api.deposit;

import io.ipbreaker.wallet.application.deposit.DepositQueryService;
import io.ipbreaker.wallet.common.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class DepositController {
    private final DepositQueryService service;

    public DepositController(DepositQueryService service) {
        this.service = service;
    }

    @GetMapping("/users/{userId}/deposits")
    public ApiResponse<List<DepositResponse>> findByUser(
            @PathVariable @NotBlank @Size(max = 64) String userId) {
        return ApiResponse.success(service.findByUserId(userId).stream()
                .map(DepositResponse::from)
                .toList());
    }

    @GetMapping("/deposits/{depositId}")
    public ApiResponse<DepositResponse> get(@PathVariable @Positive long depositId) {
        return ApiResponse.success(DepositResponse.from(service.get(depositId)));
    }
}
