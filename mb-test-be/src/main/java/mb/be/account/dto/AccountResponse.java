package mb.be.account.dto;


public record AccountResponse(
        Long id,
        String accountNumber,
        String customerId
) {}
