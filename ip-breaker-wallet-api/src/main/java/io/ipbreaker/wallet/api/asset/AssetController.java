package io.ipbreaker.wallet.api.asset;

import io.ipbreaker.wallet.application.asset.AssetService;
import io.ipbreaker.wallet.common.api.ApiResponse;
import io.ipbreaker.wallet.domain.asset.Asset;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {
    private final AssetService service;

    public AssetController(AssetService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Asset>> list() {
        return ApiResponse.success(service.listDepositEnabled());
    }
}
