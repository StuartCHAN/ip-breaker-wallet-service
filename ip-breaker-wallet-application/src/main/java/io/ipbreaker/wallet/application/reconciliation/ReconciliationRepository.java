package io.ipbreaker.wallet.application.reconciliation;

import java.math.BigInteger;
import java.util.List;

public interface ReconciliationRepository {
    List<ReconciliationDifference> findLedgerBalanceDifferences();

    List<ReconciliationDifference> findDepositLedgerDifferences();

    List<OnChainBalanceTarget> findOnChainBalanceTargets(String networkCode);

    BigInteger findPlatformLedgerBalance(String networkCode, String assetCode);

    void replaceResults(String checkType, List<ReconciliationDifference> differences);
}
