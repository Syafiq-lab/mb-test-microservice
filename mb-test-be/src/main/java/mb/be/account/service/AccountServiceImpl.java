package mb.be.account.service;

import mb.be.account.domain.Account;
import mb.be.account.domain.AccountRepository;
import mb.be.account.dto.AccountResponse;
import mb.be.account.mapper.AccountMapper;
import mb.be.common.exception.NotFoundException;
import mb.be.common.logging.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private final AccountRepository repository;
    private final AccountMapper mapper;

    @Override
    public AccountResponse getByAccountNumber(String accountNumber) {
        final long startNanos = System.nanoTime();
        final String maskedAcc = LogUtils.maskAccountNumber(accountNumber);

        log.info("Get account requested accountNumber={}", maskedAcc);

        try {
            Account account = repository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new NotFoundException("Account not found for accountNumber=" + maskedAcc));

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Get account succeeded accountNumber={} tookMs={}", maskedAcc, tookMs);

            return mapper.toResponse(account);
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Get account failed accountNumber={} tookMs={} error={}",
                    maskedAcc, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }
}
