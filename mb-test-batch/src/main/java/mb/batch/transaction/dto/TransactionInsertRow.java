package mb.batch.transaction.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionInsertRow {
	private Long version;          // @Version column in BE
	private Long accountId;        // FK from account table
	private BigDecimal amount;
	private String description;
	private LocalDate trxDate;
	private LocalTime trxTime;
	private String customerId;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
}
