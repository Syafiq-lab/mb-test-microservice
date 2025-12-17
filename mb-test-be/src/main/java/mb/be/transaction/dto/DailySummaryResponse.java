package mb.be.transaction.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record DailySummaryResponse(
		String accountNumber,
		LocalDate date,
		BigDecimal totalAmount,
		long transactionCount
) {
}
