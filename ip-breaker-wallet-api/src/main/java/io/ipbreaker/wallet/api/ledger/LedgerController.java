package io.ipbreaker.wallet.api.ledger;

import io.ipbreaker.wallet.application.ledger.LedgerQueryService;
import io.ipbreaker.wallet.common.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users/{userId}")
public class LedgerController {
    private final LedgerQueryService service;

    public LedgerController(LedgerQueryService service) {
        this.service = service;
    }

    @GetMapping("/balances")
    public ApiResponse<List<BalanceResponse>> balances(
            @PathVariable @NotBlank @Size(max = 64) String userId) {
        return ApiResponse.success(service.findBalances(userId).stream()
                .map(BalanceResponse::from)
                .toList());
    }

    @GetMapping("/ledger-transactions")
    public ApiResponse<List<LedgerTransactionResponse>> transactions(
            @PathVariable @NotBlank @Size(max = 64) String userId) {
        return ApiResponse.success(service.findTransactions(userId).stream()
                .map(LedgerTransactionResponse::from)
                .toList());
    }
}
