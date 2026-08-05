package io.ipbreaker.wallet.application.deposit;

import io.ipbreaker.wallet.domain.deposit.Deposit;
import java.util.List;
import java.util.Optional;

public interface DepositRepository {
    void insertMatching(long networkId, DepositCandidate candidate);

    List<Deposit> findByUserId(String userId);

    Optional<Deposit> findById(long depositId);
}
