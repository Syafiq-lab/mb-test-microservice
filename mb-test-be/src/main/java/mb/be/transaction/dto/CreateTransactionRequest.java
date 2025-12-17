package mb.be.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Builder
public record CreateTransactionRequest(
		@NotBlank String accountNumber,
		@NotNull @Positive BigDecimal amount,
		String description,
		@NotNull LocalDate trxDate,
		@NotNull LocalTime trxTime,
		@NotBlank String customerId
) {
}
