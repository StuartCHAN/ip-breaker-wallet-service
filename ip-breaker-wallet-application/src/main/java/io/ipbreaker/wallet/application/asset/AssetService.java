package io.ipbreaker.wallet.application.asset;

import io.ipbreaker.wallet.domain.asset.Asset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssetService {
    private final AssetRepository repository;

    public AssetService(AssetRepository repository) {
        this.repository = repository;
    }

    public List<Asset> listDepositEnabled() {
        return repository.findDepositEnabled();
    }
}
