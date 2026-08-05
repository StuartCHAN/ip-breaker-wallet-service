package io.ipbreaker.wallet.api.deposit;

import io.ipbreaker.wallet.domain.deposit.Deposit;
import java.time.Instant;

public record DepositResponse(
        long id,
        String networkCode,
        String assetCode,
        String assetType,
        String symbol,
        int decimals,
        String userId,
        String transactionHash,
        int logIndex,
        String fromAddress,
        String toAddress,
        String amountRaw,
        long blockNumber,
        int confirmations,
        String status,
        Instant detectedAt) {
    public static DepositResponse from(Deposit deposit) {
        return new DepositResponse(
                deposit.id(),
                deposit.networkCode(),
                deposit.assetCode(),
                deposit.assetType(),
                deposit.symbol(),
                deposit.decimals(),
                deposit.userId(),
                deposit.transactionHash(),
                deposit.logIndex(),
                deposit.fromAddress(),
                deposit.toAddress(),
                deposit.amountRaw().toString(),
                deposit.blockNumber(),
                deposit.confirmations(),
                deposit.status(),
                deposit.detectedAt());
    }
}
