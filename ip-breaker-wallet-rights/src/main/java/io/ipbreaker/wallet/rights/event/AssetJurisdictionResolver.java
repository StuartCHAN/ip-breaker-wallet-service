package io.ipbreaker.wallet.rights.event;

import java.math.BigInteger;

public interface AssetJurisdictionResolver {
    String resolve(String registryAddress, BigInteger assetId, long blockNumber);
}
