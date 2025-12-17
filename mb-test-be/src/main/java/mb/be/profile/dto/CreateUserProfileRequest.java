package mb.be.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserProfileRequest(
		@NotBlank String customerId,
		@NotBlank String fullName,
		@NotBlank @Email String email
) {}
