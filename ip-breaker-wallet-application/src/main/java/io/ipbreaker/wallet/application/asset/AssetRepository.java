package io.ipbreaker.wallet.application.asset;

import io.ipbreaker.wallet.domain.asset.Asset;
import java.util.List;

public interface AssetRepository {
    List<Asset> findDepositEnabled();
}
