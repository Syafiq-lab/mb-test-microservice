package mb.be.profile.service;

import mb.be.common.exception.NotFoundException;
import mb.be.common.logging.LogUtils;
import mb.be.profile.domain.UserProfile;
import mb.be.profile.domain.UserProfileRepository;
import mb.be.profile.dto.CreateUserProfileRequest;
import mb.be.profile.dto.UserProfileResponse;
import mb.be.profile.mapper.UserProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository repository;
    private final UserProfileMapper mapper;

    @Override
    @Transactional
    public UserProfileResponse createUserProfile(CreateUserProfileRequest request) {
        final long startNanos = System.nanoTime();
        final String maskedCustomerId = LogUtils.maskId(request.customerId());

        log.info("Create user profile requested customerId={}", maskedCustomerId);

        try {
            if (repository.existsByCustomerId(request.customerId())) {
                throw new IllegalArgumentException("UserProfile already exists for customerId=" + maskedCustomerId);
            }
            if (repository.existsByEmail(request.email())) {
                throw new IllegalArgumentException("UserProfile already exists for email=" + LogUtils.maskEmail(request.email()));
            }

            UserProfile entity = mapper.toEntity(request);
            UserProfile saved = repository.save(entity);

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Create user profile succeeded customerId={} tookMs={}", maskedCustomerId, tookMs);

            return mapper.toResponse(saved);
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Create user profile failed customerId={} tookMs={} error={}",
                    maskedCustomerId, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }

    @Override
    public UserProfileResponse getUserProfile(String customerId) {
        final long startNanos = System.nanoTime();
        final String maskedCustomerId = LogUtils.maskId(customerId);

        log.info("Get user profile requested customerId={}", maskedCustomerId);

        try {
            UserProfile profile = repository.findByCustomerId(customerId)
                    .orElseThrow(() -> new NotFoundException("UserProfile not found for customerId=" + maskedCustomerId));

            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("Get user profile succeeded customerId={} tookMs={}", maskedCustomerId, tookMs);

            return mapper.toResponse(profile);
        } catch (RuntimeException ex) {
            long tookMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.warn("Get user profile failed customerId={} tookMs={} error={}",
                    maskedCustomerId, tookMs, ex.getClass().getSimpleName(), ex);
            throw ex;
        }
    }
}
