package mb.be.transaction.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Builder
public record TransactionResponse(
		Long id,
		Long version,
		String accountNumber,
		BigDecimal amount,
		String description,
		LocalDate trxDate,
		LocalTime trxTime,
		String customerId
) {}
