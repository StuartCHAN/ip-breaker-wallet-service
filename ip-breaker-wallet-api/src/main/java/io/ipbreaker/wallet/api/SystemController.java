package io.ipbreaker.wallet.api;

import io.ipbreaker.wallet.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    @GetMapping("/status")
    ApiResponse<Map<String, String>> status() {
        return ApiResponse.success(Map.of("service", "ip-breaker-wallet-service", "status", "UP"));
    }
}

