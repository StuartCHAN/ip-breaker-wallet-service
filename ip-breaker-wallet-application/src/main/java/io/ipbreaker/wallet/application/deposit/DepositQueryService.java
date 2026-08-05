package io.ipbreaker.wallet.application.deposit;

import io.ipbreaker.wallet.domain.deposit.Deposit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositQueryService {
    private final DepositRepository repository;

    public DepositQueryService(DepositRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Deposit> findByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Deposit get(long depositId) {
        return repository.findById(depositId)
                .orElseThrow(() -> new DepositNotFoundException(depositId));
    }
}
