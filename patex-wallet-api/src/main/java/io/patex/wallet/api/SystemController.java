package io.patex.wallet.api;

import io.patex.wallet.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    @GetMapping("/status")
    ApiResponse<Map<String, String>> status() {
        return ApiResponse.success(Map.of("service", "patex-wallet-service", "status", "UP"));
    }
}

