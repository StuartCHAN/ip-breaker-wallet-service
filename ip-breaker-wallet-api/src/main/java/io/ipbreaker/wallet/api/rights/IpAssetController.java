package io.ipbreaker.wallet.api.rights;

import io.ipbreaker.wallet.application.rights.RightsQueryService;
import io.ipbreaker.wallet.common.api.ApiResponse;
import io.ipbreaker.wallet.rights.query.RightsQueryRepository.IpAssetView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigInteger;
import java.util.Locale;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/ip-assets")
public class IpAssetController {
    private final RightsQueryService queryService;

    public IpAssetController(RightsQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{assetId}")
    public ApiResponse<IpAssetView> get(
            @PathVariable BigInteger assetId,
            @RequestParam(defaultValue = "SEPOLIA") String network) {
        requireNonNegative(assetId);
        return ApiResponse.success(queryService.asset(network.toUpperCase(Locale.ROOT), assetId));
    }

    @GetMapping("/{assetId}/timeline")
    public ApiResponse<RightsQueryService.TimelinePage> timeline(
            @PathVariable BigInteger assetId,
            @RequestParam(defaultValue = "SEPOLIA") String network,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        requireNonNegative(assetId);
        return ApiResponse.success(queryService.timeline(
                network.toUpperCase(Locale.ROOT), assetId, cursor, limit));
    }

    private void requireNonNegative(BigInteger id) {
        if (id.signum() < 0) {
            throw new IllegalArgumentException("ID must be non-negative");
        }
    }
}
