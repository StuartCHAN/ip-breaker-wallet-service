package io.ipbreaker.wallet.application.reconciliation;

import java.math.BigInteger;
import java.util.List;

public interface ReconciliationRepository {
    List<ReconciliationDifference> findLedgerBalanceDifferences(String networkCode);

    List<ReconciliationDifference> findDepositLedgerDifferences(String networkCode);

    List<OnChainBalanceTarget> findOnChainBalanceTargets(String networkCode);

    BigInteger findPlatformLedgerBalance(String networkCode, String assetCode);

    void replaceResults(
            String checkType, String networkCode, List<ReconciliationDifference> differences);
}
