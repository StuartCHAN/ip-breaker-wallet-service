package io.ipbreaker.wallet.api.settlement;

import io.ipbreaker.wallet.application.settlement.ObligationView;
import io.ipbreaker.wallet.application.settlement.SettlementEligibilityService;
import io.ipbreaker.wallet.application.settlement.TermsManifest;
import io.ipbreaker.wallet.application.settlement.TermsManifestHash;
import io.ipbreaker.wallet.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.math.BigInteger;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/license-agreements")
public class SettlementEligibilityController {
    private final SettlementEligibilityService service;

    public SettlementEligibilityController(SettlementEligibilityService service) {
        this.service = service;
    }

    @PostMapping("/terms-manifests/hash")
    public ApiResponse<TermsManifestHash> hash(@Valid @RequestBody TermsManifestRequest request) {
        return ApiResponse.success(service.prepare(toManifest(request)));
    }

    @PostMapping("/{agreementId}/terms-manifests")
    public ApiResponse<ObligationView> register(
            @PathVariable BigInteger agreementId,
            @RequestParam(defaultValue = "SEPOLIA") String network,
            @Valid @RequestBody TermsManifestRequest request) {
        return ApiResponse.success(service.register(
                network.toUpperCase(Locale.ROOT), agreementId, toManifest(request)));
    }

    @GetMapping("/{agreementId}/payment-obligation")
    public ApiResponse<ObligationView> get(
            @PathVariable BigInteger agreementId,
            @RequestParam(defaultValue = "SEPOLIA") String network) {
        if (agreementId.signum() < 0) {
            throw new IllegalArgumentException("ID must be non-negative");
        }
        return ApiResponse.success(service.find(network.toUpperCase(Locale.ROOT), agreementId));
    }

    private TermsManifest toManifest(TermsManifestRequest request) {
        return new TermsManifest(
                request.schemaVersion(), request.termsVersion(), request.assetId(), request.licensor(),
                request.licensee(), request.payer(), request.payee(),
                request.currency().toUpperCase(Locale.ROOT), request.amount());
    }
}
