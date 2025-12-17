package mb.be.transaction.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    Page<Transaction> findByAccount_AccountNumber(String accountNumber, Pageable pageable);

    Page<Transaction> findByAccount_AccountNumberAndTrxDateBetween(
            String accountNumber,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    );
}
