package mb.be.transaction.service;

import mb.be.transaction.dto.CreateTransactionRequest;
import mb.be.transaction.dto.DailySummaryResponse;
import mb.be.transaction.dto.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface TransactionService {

    TransactionResponse create(CreateTransactionRequest request);

    Page<TransactionResponse> listByAccount(String accountNumber, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    Page<TransactionResponse> search(String customerId, List<String> accountNumbers, String description,
                                     LocalDate fromDate, LocalDate toDate, Pageable pageable);

    TransactionResponse getById(Long id);

    TransactionResponse updateDescription(Long id, String description, String ifMatch);

    DailySummaryResponse dailySummary(String accountNumber, LocalDate date);
}
