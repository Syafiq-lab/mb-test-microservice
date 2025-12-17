package mb.be.account.service;

import mb.be.account.dto.AccountResponse;

public interface AccountService {

    AccountResponse getByAccountNumber(String accountNumber);
}
