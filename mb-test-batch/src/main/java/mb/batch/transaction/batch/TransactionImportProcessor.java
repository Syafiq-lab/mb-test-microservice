package mb.batch.transaction.batch;

import lombok.RequiredArgsConstructor;
import mb.batch.transaction.dto.TransactionFileRow;
import mb.batch.transaction.dto.TransactionInsertRow;
import mb.batch.transaction.exception.InvalidTransactionRecordException;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class TransactionImportProcessor implements ItemProcessor<TransactionFileRow, TransactionInsertRow> {

    private final NamedParameterJdbcTemplate jdbc;

    private final ConcurrentHashMap<String, Long> userProfileIdCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> accountIdCache = new ConcurrentHashMap<>();

    @Override
    public TransactionInsertRow process(TransactionFileRow item) {
        if (item == null) return null;

        String accountNumber = trim(item.getAccountNumber());
        String customerId = trim(item.getCustomerId());

        if (accountNumber.isEmpty() || customerId.isEmpty()) return null;

        if (item.getTrxAmount() == null) {
            throw new InvalidTransactionRecordException("trxAmount is null for account=" + accountNumber);
        }
        if (item.getTrxDate() == null || item.getTrxTime() == null) {
            throw new InvalidTransactionRecordException("trxDate/trxTime is null for account=" + accountNumber);
        }

        long userProfileId = resolveOrCreateUserProfileId(customerId);
        long accountId = resolveOrCreateAccountId(accountNumber, userProfileId);

        LocalDateTime now = LocalDateTime.now();
        return TransactionInsertRow.builder()
                .version(0L)
                .accountId(accountId)
                .amount(item.getTrxAmount())
                .description(item.getDescription())
                .trxDate(item.getTrxDate())
                .trxTime(item.getTrxTime())
                .customerId(customerId)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private long resolveOrCreateUserProfileId(String customerId) {
        return userProfileIdCache.computeIfAbsent(customerId, cid -> {
            // First, try to find existing user profile
            Long existingId = findUserProfileId(cid);
            if (existingId != null) {
                return existingId;
            }

            // If not found, create new user profile
            try {
                jdbc.update(
                    "INSERT INTO user_profile (customer_id, full_name, email) VALUES (:customerId, :fullName, :email)",
                    Map.of(
                        "customerId", cid,
                        "fullName", "IMPORTED-" + cid,
                        "email", cid + "@import.local"
                    )
                );

                Long newId = findUserProfileId(cid);
                if (newId == null) {
                    throw new InvalidTransactionRecordException("Failed to create or retrieve user_profile for customerId: " + cid);
                }
                return newId;
            } catch (DataAccessException e) {
                throw new InvalidTransactionRecordException("Error processing user_profile for customerId: " + e);
            }
        });
    }

    private Long findUserProfileId(String customerId) {
        try {
            return jdbc.queryForObject(
                "SELECT id FROM user_profile WHERE customer_id = :cid",
                Map.of("cid", customerId),
                Long.class
            );
        } catch (DataAccessException e) {
            return null;
        }
    }

    private long resolveOrCreateAccountId(String accountNumber, long userProfileId) {
        return accountIdCache.computeIfAbsent(accountNumber, acc -> {
            // First, try to find existing account
            Long existingId = findAccountId(acc);
            if (existingId != null) {
                return existingId;
            }

            // If not found, create new account
            try {
                jdbc.update(
                    "INSERT INTO account (account_number, user_profile_id) VALUES (:accountNumber, :userProfileId)",
                    Map.of("accountNumber", acc, "userProfileId", userProfileId)
                );

                Long newId = findAccountId(acc);
                if (newId == null) {
                    throw new InvalidTransactionRecordException("Failed to create or retrieve account for accountNumber: " + acc);
                }
                return newId;
            } catch (DataAccessException e) {
                throw new InvalidTransactionRecordException("Error processing account for accountNumber: " + e);
            }
        });
    }

    private Long findAccountId(String accountNumber) {
        try {
            return jdbc.queryForObject(
                "SELECT id FROM account WHERE account_number = :acc",
                Map.of("acc", accountNumber),
                Long.class
            );
        } catch (DataAccessException e) {
            return null;
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
