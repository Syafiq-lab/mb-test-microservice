package mb.be.transaction.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTransactionDescriptionRequest(
		@NotBlank String description
) {}
