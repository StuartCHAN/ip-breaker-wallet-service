package io.ipbreaker.wallet.api.audit;

import io.ipbreaker.wallet.application.audit.SettlementAssuranceStatus;
import io.ipbreaker.wallet.application.audit.SettlementAuditService;
import io.ipbreaker.wallet.application.audit.SettlementAuditTrail;
import io.ipbreaker.wallet.application.audit.SettlementProofPackage;
import io.ipbreaker.wallet.common.api.ApiResponse;
import java.math.BigInteger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/license-agreements/{agreementId}")
public class SettlementAuditController {
    private final SettlementAuditService service;

    public SettlementAuditController(SettlementAuditService service) {
        this.service = service;
    }

    @GetMapping("/audit-trail")
    public ApiResponse<SettlementAuditTrail> trail(
            @PathVariable BigInteger agreementId,
            @RequestParam(defaultValue = "SEPOLIA") String network) {
        return ApiResponse.success(service.trail(network, agreementId));
    }

    @GetMapping("/assurance-status")
    public ApiResponse<SettlementAssuranceStatus> assurance(
            @PathVariable BigInteger agreementId,
            @RequestParam(defaultValue = "SEPOLIA") String network) {
        return ApiResponse.success(service.assurance(network, agreementId));
    }

    @PostMapping("/settlement-proof-package")
    public ApiResponse<SettlementProofPackage> generate(
            @PathVariable BigInteger agreementId,
            @RequestParam(defaultValue = "SEPOLIA") String network) {
        return ApiResponse.success(service.generate(network, agreementId));
    }
}
