package mb.batch.transaction.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionFileRow {
	private String accountNumber;
	private BigDecimal trxAmount;
	private String description;
	private LocalDate trxDate;
	private LocalTime trxTime;
	private String customerId;
}
