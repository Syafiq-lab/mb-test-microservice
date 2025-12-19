package mb.batch.transaction.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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

        // Visible per-item log (lower to DEBUG later if too noisy)
        log.info("[PROCESS] acct={} cust={} amount={} date={} time={} desc={}",
                accountNumber, customerId, item.getTrxAmount(), item.getTrxDate(), item.getTrxTime(), item.getDescription());

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
            try {
                // H2 UPSERT: MERGE ... KEY(...) :contentReference[oaicite:3]{index=3}
                jdbc.update(
                        """
                        MERGE INTO USER_PROFILE (CUSTOMER_ID, FULL_NAME, EMAIL)
                        KEY (CUSTOMER_ID)
                        VALUES (:customerId, :fullName, :email)
                        """,
                        Map.of(
                                "customerId", cid,
                                "fullName", "IMPORTED-" + cid,
                                "email", cid + "@import.local"
                        )
                );

                Long id = jdbc.queryForObject(
                        "SELECT ID FROM USER_PROFILE WHERE CUSTOMER_ID = :cid",
                        Map.of("cid", cid),
                        Long.class
                );

                if (id == null) {
                    throw new InvalidTransactionRecordException("USER_PROFILE MERGE succeeded but ID not found for customerId=" + cid);
                }
                return id;
            } catch (DataAccessException e) {
                // BadSqlGrammarException is a DataAccessException; log root cause message for real reason. :contentReference[oaicite:4]{index=4}
                String root = (e.getMostSpecificCause() != null) ? e.getMostSpecificCause().getMessage() : e.getMessage();
                log.error("[SQL] user_profile upsert/select failed customerId={} root={}", cid, root, e);
                throw new InvalidTransactionRecordException("Error processing user_profile for customerId: " + root);
            }
        });
    }

    private long resolveOrCreateAccountId(String accountNumber, long userProfileId) {
        return accountIdCache.computeIfAbsent(accountNumber, acc -> {
            try {
                jdbc.update(
                        """
                        MERGE INTO ACCOUNT (ACCOUNT_NUMBER, USER_PROFILE_ID)
                        KEY (ACCOUNT_NUMBER)
                        VALUES (:accountNumber, :userProfileId)
                        """,
                        Map.of(
                                "accountNumber", acc,
                                "userProfileId", userProfileId
                        )
                );

                Long id = jdbc.queryForObject(
                        "SELECT ID FROM ACCOUNT WHERE ACCOUNT_NUMBER = :acc",
                        Map.of("acc", acc),
                        Long.class
                );

                if (id == null) {
                    throw new InvalidTransactionRecordException("ACCOUNT MERGE succeeded but ID not found for accountNumber=" + acc);
                }
                return id;
            } catch (DataAccessException e) {
                String root = (e.getMostSpecificCause() != null) ? e.getMostSpecificCause().getMessage() : e.getMessage();
                log.error("[SQL] account upsert/select failed accountNumber={} root={}", acc, root, e);
                throw new InvalidTransactionRecordException("Error processing account for accountNumber: " + root);
            }
        });
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
