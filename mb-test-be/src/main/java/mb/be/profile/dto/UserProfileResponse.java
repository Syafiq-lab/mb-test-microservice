package mb.be.profile.dto;

public record UserProfileResponse(
        Long id,
        String customerId,
        String fullName,
        String email
) {}
