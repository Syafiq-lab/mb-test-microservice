package mb.batch.transaction.batch;

import lombok.RequiredArgsConstructor;
import mb.batch.transaction.dto.TransactionFileRow;
import mb.batch.transaction.dto.TransactionInsertRow;
import mb.batch.transaction.exception.InvalidTransactionRecordException;
import org.springframework.batch.infrastructure.item.ItemProcessor;
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
            // Upsert by customer_id
            jdbc.update("""
                MERGE INTO user_profile (customer_id, full_name, email)
                KEY (customer_id)
                VALUES (:customerId, :fullName, :email)
            """, Map.of(
                    "customerId", cid,
                    "fullName", "IMPORTED-" + cid,
                    "email", cid + "@import.local"
            ));

            Long id = jdbc.queryForObject(
                    "select id from user_profile where customer_id = :cid",
                    Map.of("cid", cid),
                    Long.class
            );
            if (id == null) throw new InvalidTransactionRecordException("user_profile id not found after upsert: " + cid);
            return id;
        });
    }

    private long resolveOrCreateAccountId(String accountNumber, long userProfileId) {
        return accountIdCache.computeIfAbsent(accountNumber, acc -> {
            // Upsert by account_number, always points to this user_profile_id if created
            jdbc.update("""
                MERGE INTO account (account_number, user_profile_id)
                KEY (account_number)
                VALUES (:acc, :upid)
            """, Map.of("acc", acc, "upid", userProfileId));

            Long id = jdbc.queryForObject(
                    "select id from account where account_number = :acc",
                    Map.of("acc", acc),
                    Long.class
            );
            if (id == null) throw new InvalidTransactionRecordException("account id not found after upsert: " + acc);
            return id;
        });
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
