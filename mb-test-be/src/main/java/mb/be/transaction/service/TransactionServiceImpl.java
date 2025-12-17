package mb.be.transaction.service;

import mb.be.account.domain.Account;
import mb.be.account.domain.AccountRepository;
import mb.be.common.exception.NotFoundException;
import mb.be.common.logging.LogUtils;
import mb.be.transaction.domain.Transaction;
import mb.be.transaction.domain.TransactionRepository;
import mb.be.transaction.domain.TransactionSpecifications;
import mb.be.transaction.dto.CreateTransactionRequest;
import mb.be.transaction.dto.DailySummaryResponse;
import mb.be.transaction.dto.TransactionResponse;
import mb.be.transaction.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionMapper transactionMapper;

    @Override
    @Transactional
    public TransactionResponse create(CreateTransactionRequest request) {
        final long startNanos = System.nanoTime();
        final String maskedAcc = LogUtils.maskAccountNumber(request.accountNumber());

        log.info("Create api (service) start accountNumber={}", maskedAcc);

        try {
            Account account = accountRepository.findByAccountNumber(request.accountNumber())
                    .orElseThrow(() -> new NotFoundException("Account not found"));

            Transaction entity = transactionMapper.toEntity(request, account);
            Transaction saved = transactionRepository.save(entity);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Create api (service) ok accountNumber={} tookMs={}", maskedAcc, tookMs);

            return transactionMapper.toResponse(saved);
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Create api (service) failed accountNumber={} tookMs={} error={}",
                    maskedAcc, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }

    @Override
    public Page<TransactionResponse> listByAccount(String accountNumber, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        final long startNanos = System.nanoTime();
        final String maskedAcc = LogUtils.maskAccountNumber(accountNumber);

        log.info("List transactions (service) start accountNumber={} fromDate={} toDate={} page={} size={} sort={}",
                maskedAcc, fromDate, toDate,
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());

        try {
            Page<Transaction> page =
                    (fromDate != null && toDate != null)
                            ? transactionRepository.findByAccount_AccountNumberAndTrxDateBetween(accountNumber, fromDate, toDate, pageable)
                        : transactionRepository.findByAccount_AccountNumber(accountNumber, pageable);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("List transactions (service) ok accountNumber={} totalElements={} totalPages={} tookMs={}",
                    maskedAcc, page.getTotalElements(), page.getTotalPages(), tookMs);

            return page.map(transactionMapper::toResponse);
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("List transactions (service) failed accountNumber={} tookMs={} error={}",
                    maskedAcc, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }

    @Override
    public Page<TransactionResponse> search(
            String customerId,
            List<String> accountNumbers,
            String description,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        final long startNanos = System.nanoTime();
        final boolean hasCustomerId = customerId != null && !customerId.isBlank();
        final int accCount = accountNumbers == null ? 0 : accountNumbers.size();
        final boolean hasDesc = description != null && !description.isBlank();

        log.info("Search transactions (service) start customerIdPresent={} accountNumbersCount={} descriptionPresent={} fromDate={} toDate={} page={} size={} sort={}",
                hasCustomerId, accCount, hasDesc, fromDate, toDate,
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());

        try {
            Specification<Transaction> spec = Specification
                    .where(TransactionSpecifications.customerIdEquals(customerId))
                    .and(TransactionSpecifications.accountNumberIn(accountNumbers))
                    .and(TransactionSpecifications.descriptionContains(description))
                    .and(TransactionSpecifications.trxDateBetween(fromDate, toDate));

            Page<TransactionResponse> page = transactionRepository.findAll(spec, pageable)
                    .map(transactionMapper::toResponse);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Search transactions (service) ok totalElements={} totalPages={} tookMs={}",
                    page.getTotalElements(), page.getTotalPages(), tookMs);

            return page;
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Search transactions (service) failed tookMs={} error={}",
                    tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }


    @Override
    public TransactionResponse getById(Long id) {
        final long startNanos = System.nanoTime();
        log.info("Get transaction (service) start id={}", id);

        try {
            Transaction tx = transactionRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Transaction not found id=" + id));

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Get transaction (service) ok id={} tookMs={}", id, tookMs);

            return transactionMapper.toResponse(tx);
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Get transaction (service) failed id={} tookMs={} error={}", id, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }

    @Override
    @Transactional
    public TransactionResponse updateDescription(Long id, String description, String ifMatch) {
        final long startNanos = System.nanoTime();
        final int descLen = description == null ? 0 : description.length();
        final boolean ifMatchPresent = ifMatch != null && !ifMatch.isBlank();

        log.info("Update description (service) start id={} ifMatchPresent={} descriptionLen={}",
                id, ifMatchPresent, descLen);

        if (!ifMatchPresent) {
            log.warn("Update description (service) rejected id={} reason=missing_if_match", id);
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Missing If-Match header");
        }

        try {
            Transaction tx = transactionRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Transaction not found id=" + id));

            long expected;
            try {
                expected = parseIfMatchToLong(ifMatch);
            } catch (ResponseStatusException ex) {
                log.warn("Update description (service) rejected id={} reason=invalid_if_match value={}", id, ifMatch, ex);
                throw ex;
            }

            long current = tx.getVersion() == null ? -1L : tx.getVersion();

            if (expected != current) {
                log.warn("Update description (service) rejected id={} reason=etag_mismatch expected={} current={}",
                        id, expected, current);
                throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "ETag mismatch");
            }

            tx.setDescription(description);
            tx.setUpdatedAt(LocalDateTime.now());

            Transaction saved = transactionRepository.save(tx);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            long newVersion = saved.getVersion() == null ? -1L : saved.getVersion();
            log.info("Update description (service) ok id={} newVersion={} tookMs={}", id, newVersion, tookMs);

            return transactionMapper.toResponse(saved);
        } catch (OptimisticLockingFailureException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Update description (service) failed id={} reason=optimistic_lock tookMs={}", id, tookMs, ex);
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "ETag mismatch");
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Update description (service) failed id={} tookMs={} error={}",
                    id, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }


    private static long parseIfMatchToLong(String ifMatch) {
        String s = ifMatch.trim();
        if (s.startsWith("W/")) s = s.substring(2).trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid If-Match header");
        }
    }

    @Override
    public DailySummaryResponse dailySummary(String accountNumber, LocalDate date) {
        final long startNanos = System.nanoTime();
        final String maskedAcc = LogUtils.maskAccountNumber(accountNumber);

        log.info("Daily summary (service) start accountNumber={} date={}", maskedAcc, date);

        try {
            var page = transactionRepository.findByAccount_AccountNumberAndTrxDateBetween(
                    accountNumber, date, date, Pageable.unpaged()
            );

            BigDecimal total = page.getContent().stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long count = page.getTotalElements();

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Daily summary (service) ok accountNumber={} date={} count={} totalAmount={} tookMs={}",
                    maskedAcc, date, count, total, tookMs);

            return DailySummaryResponse.builder()
                    .accountNumber(accountNumber)
                    .date(date)
                    .totalAmount(total)
                    .transactionCount(count)
                    .build();
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Daily summary (service) failed accountNumber={} date={} tookMs={} error={}",
                    maskedAcc, date, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }

}
