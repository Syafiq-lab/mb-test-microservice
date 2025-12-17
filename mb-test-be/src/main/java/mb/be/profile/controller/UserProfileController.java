package mb.be.profile.controller;

import mb.be.common.api.ApiResponse;
import mb.be.common.logging.LogUtils;
import mb.be.profile.dto.CreateUserProfileRequest;
import mb.be.profile.dto.UserProfileResponse;
import mb.be.profile.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService service;

    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> create(
            @RequestBody @Valid CreateUserProfileRequest request
    ) {
        final long startNanos = System.nanoTime();
        final String maskedCustomerId = LogUtils.maskId(request.customerId());

        log.info("Create user profile requested customerId={}", maskedCustomerId);

        try {
            UserProfileResponse created = service.createUserProfile(request);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Create user profile succeeded customerId={} tookMs={}",
                    LogUtils.maskId(created.customerId()), tookMs);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("User profile created successfully", created));
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Create user profile failed customerId={} tookMs={} error={}",
                    maskedCustomerId, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getByCustomerId(
            @PathVariable String customerId
    ) {
        final long startNanos = System.nanoTime();
        final String maskedCustomerId = LogUtils.maskId(customerId);

        log.debug("Get user profile requested customerId={}", maskedCustomerId);

        try {
            UserProfileResponse profile = service.getUserProfile(customerId);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Get user profile succeeded customerId={} tookMs={}", maskedCustomerId, tookMs);

            return ResponseEntity.ok(ApiResponse.success("User profile fetched successfully", profile));
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Get user profile failed customerId={} tookMs={} error={}",
                    maskedCustomerId, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }
}
