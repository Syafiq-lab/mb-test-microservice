package mb.be.transaction.controller;

import mb.be.common.api.ApiResponse;
import mb.be.common.api.PageResponse;
import mb.be.common.logging.LogUtils;
import mb.be.transaction.dto.CreateTransactionRequest;
import mb.be.transaction.dto.DailySummaryResponse;
import mb.be.transaction.dto.TransactionResponse;
import mb.be.transaction.dto.UpdateTransactionDescriptionRequest;
import mb.be.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    @PostMapping("/transactions")
    public ResponseEntity<ApiResponse<TransactionResponse>> create(@RequestBody @Valid CreateTransactionRequest request) {
        final long startNanos = System.nanoTime();
        final String maskedAcc = LogUtils.maskAccountNumber(request.accountNumber());

        log.info("Create api requested accountNumber={}", maskedAcc);

        TransactionResponse created = service.create(request);

        long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
        log.info("Create api succeeded accountNumber={} tookMs={}", maskedAcc, tookMs);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transaction created successfully", created));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> search(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false, name = "accountNumber") List<String> accountNumbers,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable
    ) {
        final long startNanos = System.nanoTime();
        final int accCount = (accountNumbers == null) ? 0 : accountNumbers.size();
        final boolean hasCustomerId = customerId != null && !customerId.isBlank();
        final boolean hasDesc = description != null && !description.isBlank();

        log.info("Search transactions requested customerIdPresent={} accountNumbersCount={} descriptionPresent={} fromDate={} toDate={} page={} size={} sort={}",
                hasCustomerId, accCount, hasDesc, fromDate, toDate,
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());

        try {
            Page<TransactionResponse> page = service.search(customerId, accountNumbers, description, fromDate, toDate, pageable);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Search transactions succeeded totalElements={} totalPages={} tookMs={}",
                    page.getTotalElements(), page.getTotalPages(), tookMs);

            return ResponseEntity.ok(ApiResponse.success("Transactions fetched successfully", PageResponse.from(page)));
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Search transactions failed tookMs={} error={}", tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }


    @GetMapping("/transactions/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getById(@PathVariable Long id) {
        final long startNanos = System.nanoTime();
        log.info("Get transaction requested id={}", id);

        try {
            TransactionResponse tx = service.getById(id);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Get transaction succeeded id={} tookMs={}", id, tookMs);

            return ResponseEntity.ok()
                    .eTag(String.valueOf(tx.version()))
                    .body(ApiResponse.success("Transaction fetched successfully", tx));
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Get transaction failed id={} tookMs={} error={}", id, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }


    @PatchMapping("/transactions/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> updateDescription(
            @PathVariable Long id,
            @RequestHeader("If-Match") String ifMatch,
            @RequestBody @Valid UpdateTransactionDescriptionRequest request
    ) {
        final long startNanos = System.nanoTime();
        final int descLen = (request.description() == null) ? 0 : request.description().length();
        log.info("Update transaction description requested id={} ifMatchPresent={} descriptionLen={}",
                id, ifMatch != null && !ifMatch.isBlank(), descLen);

        try {
            TransactionResponse updated = service.updateDescription(id, request.description(), ifMatch);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Update transaction description succeeded id={} newVersion={} tookMs={}",
                    id, updated.version(), tookMs);

            return ResponseEntity.ok()
                    .eTag(String.valueOf(updated.version()))
                    .body(ApiResponse.success("Transaction updated successfully", updated));
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Update transaction description failed id={} tookMs={} error={}",
                    id, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }


    @GetMapping("/accounts/{accountNumber}/transactions")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> listByAccount(
            @PathVariable String accountNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Pageable pageable
    ) {
        final long startNanos = System.nanoTime();
        final String maskedAcc = LogUtils.maskAccountNumber(accountNumber);

        log.info("List transactions requested accountNumber={} fromDate={} toDate={} page={} size={} sort={}",
                maskedAcc, fromDate, toDate,
                pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort());

        try {
            Page<TransactionResponse> page = service.listByAccount(accountNumber, fromDate, toDate, pageable);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("List transactions succeeded accountNumber={} totalElements={} totalPages={} tookMs={}",
                    maskedAcc, page.getTotalElements(), page.getTotalPages(), tookMs);

            return ResponseEntity.ok(ApiResponse.success("Transactions fetched successfully", PageResponse.from(page)));
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("List transactions failed accountNumber={} tookMs={} error={}",
                    maskedAcc, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }

    @GetMapping("/accounts/{accountNumber}/transactions/summary/daily")
    public ResponseEntity<ApiResponse<DailySummaryResponse>> dailySummary(
            @PathVariable String accountNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        final long startNanos = System.nanoTime();
        final String maskedAcc = LogUtils.maskAccountNumber(accountNumber);

        log.info("Daily summary requested accountNumber={} date={}", maskedAcc, date);

        try {
            DailySummaryResponse summary = service.dailySummary(accountNumber, date);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Daily summary succeeded accountNumber={} date={} tookMs={}", maskedAcc, date, tookMs);

            return ResponseEntity.ok(ApiResponse.success("Daily api summary fetched successfully", summary));
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Daily summary failed accountNumber={} date={} tookMs={} error={}",
                    maskedAcc, date, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }

}
