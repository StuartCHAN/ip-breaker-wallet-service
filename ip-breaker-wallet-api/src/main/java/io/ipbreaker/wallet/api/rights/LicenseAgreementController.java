package io.ipbreaker.wallet.api.rights;

import io.ipbreaker.wallet.application.rights.RightsQueryService;
import io.ipbreaker.wallet.common.api.ApiResponse;
import io.ipbreaker.wallet.rights.query.RightsQueryRepository.LicenseAgreementView;
import java.math.BigInteger;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/license-agreements")
public class LicenseAgreementController {
    private final RightsQueryService queryService;

    public LicenseAgreementController(RightsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{agreementId}")
    public ApiResponse<LicenseAgreementView> get(
            @PathVariable BigInteger agreementId,
            @RequestParam(defaultValue = "SEPOLIA") String network) {
        if (agreementId.signum() < 0) {
            throw new IllegalArgumentException("ID must be non-negative");
        }
        return ApiResponse.success(queryService.agreement(
                network.toUpperCase(Locale.ROOT), agreementId));
    }
}
