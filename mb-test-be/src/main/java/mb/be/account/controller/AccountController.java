package mb.be.account.controller;

import mb.be.account.dto.AccountResponse;
import mb.be.account.service.AccountService;
import mb.be.common.api.ApiResponse;
import mb.be.common.logging.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<ApiResponse<AccountResponse>> getByAccountNumber(
            @PathVariable String accountNumber
    ) {
        final long startNanos = System.nanoTime();
        final String maskedAcc = LogUtils.maskAccountNumber(accountNumber);

        log.info("Get account (controller) requested accountNumber={}", maskedAcc);

        try {
            AccountResponse response = accountService.getByAccountNumber(accountNumber);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Get account (controller) succeeded accountNumber={} tookMs={}", maskedAcc, tookMs);

            return ResponseEntity.ok(ApiResponse.success("Account fetched successfully", response));
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Get account (controller) failed accountNumber={} tookMs={} error={}",
                    maskedAcc, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }
}
