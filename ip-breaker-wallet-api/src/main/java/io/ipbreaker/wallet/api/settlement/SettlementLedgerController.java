package io.ipbreaker.wallet.api.settlement;

import io.ipbreaker.wallet.application.settlement.SettlementJournalView;
import io.ipbreaker.wallet.application.settlement.SettlementLedgerService;
import io.ipbreaker.wallet.application.settlement.SettlementView;
import io.ipbreaker.wallet.common.api.ApiResponse;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/license-agreements")
public class SettlementLedgerController {
    private final SettlementLedgerService service;

    public SettlementLedgerController(SettlementLedgerService service) {
        this.service = service;
    }

    @PostMapping("/{agreementId}/settlement")
    public ApiResponse<SettlementView> post(
            @PathVariable BigInteger agreementId,
            @RequestParam(defaultValue = "SEPOLIA") String network) {
        requireNonNegative(agreementId);
        return ApiResponse.success(service.post(network.toUpperCase(Locale.ROOT), agreementId));
    }

    @GetMapping("/{agreementId}/settlement")
    public ApiResponse<SettlementView> get(
            @PathVariable BigInteger agreementId,
            @RequestParam(defaultValue = "SEPOLIA") String network) {
        requireNonNegative(agreementId);
        return ApiResponse.success(service.find(network.toUpperCase(Locale.ROOT), agreementId));
    }

    @GetMapping("/settlements/{settlementId}/journals")
    public ApiResponse<List<SettlementJournalView>> journals(@PathVariable long settlementId) {
        if (settlementId <= 0) {
            throw new IllegalArgumentException("Settlement ID must be positive");
        }
        return ApiResponse.success(service.journals(settlementId));
    }

    private void requireNonNegative(BigInteger agreementId) {
        if (agreementId.signum() < 0) {
            throw new IllegalArgumentException("Agreement ID must be non-negative");
        }
    }
}
