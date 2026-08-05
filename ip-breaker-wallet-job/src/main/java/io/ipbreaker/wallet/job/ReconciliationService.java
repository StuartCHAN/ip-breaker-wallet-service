package io.ipbreaker.wallet.job;

import io.ipbreaker.wallet.application.reconciliation.OnChainBalanceTarget;
import io.ipbreaker.wallet.application.reconciliation.ReconciliationDifference;
import io.ipbreaker.wallet.application.reconciliation.ReconciliationRepository;
import io.ipbreaker.wallet.chain.BlockchainRpcClient;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationService {
    private final ReconciliationRepository repository;

    private final BlockchainRpcClient rpcClient;

    public ReconciliationService(
            ReconciliationRepository repository,
            BlockchainRpcClient rpcClient) {
        this.repository = repository;
        this.rpcClient = rpcClient;
    }

    public int reconcileLedger(String networkCode) {
        List<ReconciliationDifference> differences = repository.findLedgerBalanceDifferences(networkCode);
        repository.replaceResults("LEDGER_BALANCE", networkCode, differences);
        return differences.size();
    }

    public int reconcileDeposits(String networkCode) {
        List<ReconciliationDifference> differences = repository.findDepositLedgerDifferences(networkCode);
        repository.replaceResults("DEPOSIT_LEDGER", networkCode, differences);
        return differences.size();
    }

    public int reconcileOnChain(String networkCode) {
        Map<String, BigInteger> balances = new LinkedHashMap<>();
        for (OnChainBalanceTarget target : repository.findOnChainBalanceTargets(networkCode)) {
            BigInteger balance = "NATIVE".equals(target.assetType())
                    ? rpcClient.getNativeBalance(target.address(), target.blockNumber())
                    : rpcClient.getTokenBalance(
                            target.contractAddress(), target.address(), target.blockNumber());
            balances.merge(target.assetCode(), balance, BigInteger::add);
        }
        List<ReconciliationDifference> differences = new ArrayList<>();
        for (Map.Entry<String, BigInteger> entry : balances.entrySet()) {
            BigInteger ledger = repository.findPlatformLedgerBalance(networkCode, entry.getKey());
            if (!ledger.equals(entry.getValue())) {
                differences.add(new ReconciliationDifference(
                        "ONCHAIN_PLATFORM", networkCode, entry.getKey(), "ASSET",
                        entry.getKey(), ledger, entry.getValue(),
                        "platform ledger balance differs from custody addresses"));
            }
        }
        repository.replaceResults("ONCHAIN_PLATFORM", networkCode, differences);
        return differences.size();
    }
}
