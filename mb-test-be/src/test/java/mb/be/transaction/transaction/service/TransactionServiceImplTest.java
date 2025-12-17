package mb.be.transaction.transaction.service;

import mb.be.account.domain.Account;
import mb.be.account.domain.AccountRepository;
import mb.be.common.exception.NotFoundException;
import mb.be.transaction.domain.Transaction;
import mb.be.transaction.domain.TransactionRepository;
import mb.be.transaction.dto.CreateTransactionRequest;
import mb.be.transaction.dto.DailySummaryResponse;
import mb.be.transaction.dto.TransactionResponse;
import mb.be.transaction.mapper.TransactionMapper;
import mb.be.transaction.service.TransactionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class) // standard Mockito+JUnit5 setup :contentReference[oaicite:2]{index=2}
class TransactionServiceImplTest {

	@Mock
	TransactionRepository transactionRepository;
	@Mock
	AccountRepository accountRepository;
	@Mock
	TransactionMapper transactionMapper;

	@InjectMocks
	TransactionServiceImpl service;

	@Test
	void create_accountNotFound_throwsNotFound() {
		CreateTransactionRequest req = CreateTransactionRequest.builder()
				.accountNumber("ACC1")
				.amount(new BigDecimal("1.00"))
				.trxDate(LocalDate.of(2025, 12, 1))
				.trxTime(LocalTime.of(10, 0))
				.customerId("C1")
				.build();

		when(accountRepository.findByAccountNumber("ACC1")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(req))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Account not found");

		verify(transactionRepository, never()).save(any());
	}

	@Test
	void create_happyPath_savesAndMaps() {
		CreateTransactionRequest req = CreateTransactionRequest.builder()
				.accountNumber("ACC1")
				.amount(new BigDecimal("1.00"))
				.trxDate(LocalDate.of(2025, 12, 1))
				.trxTime(LocalTime.of(10, 0))
				.customerId("C1")
				.build();

		Account account = mock(Account.class);
		Transaction entity = mock(Transaction.class);
		Transaction saved = mock(Transaction.class);
		TransactionResponse resp = mock(TransactionResponse.class);

		when(accountRepository.findByAccountNumber("ACC1")).thenReturn(Optional.of(account));
		when(transactionMapper.toEntity(req, account)).thenReturn(entity);
		when(transactionRepository.save(entity)).thenReturn(saved);
		when(transactionMapper.toResponse(saved)).thenReturn(resp);

		TransactionResponse out = service.create(req);

		assertThat(out).isSameAs(resp);
		verify(transactionRepository).save(entity);
	}

	@Test
	void listByAccount_withFromAndTo_callsBetweenQuery() {
		Transaction tx = mock(Transaction.class);
		TransactionResponse tr = mock(TransactionResponse.class);
		Page<Transaction> page = new PageImpl<>(List.of(tx));

		when(transactionRepository.findByAccount_AccountNumberAndTrxDateBetween(
				eq("ACC1"),
				eq(LocalDate.of(2025, 12, 1)),
				eq(LocalDate.of(2025, 12, 31)),
				any(Pageable.class)
		)).thenReturn(page);

		when(transactionMapper.toResponse(tx)).thenReturn(tr);

		Page<TransactionResponse> out = service.listByAccount(
				"ACC1",
				LocalDate.of(2025, 12, 1),
				LocalDate.of(2025, 12, 31),
				PageRequest.of(0, 10)
		);

		assertThat(out.getContent()).containsExactly(tr);
		verify(transactionRepository, never()).findByAccount_AccountNumber(eq("ACC1"), any());
	}

	@Test
	void listByAccount_withoutDates_callsSimpleQuery() {
		Page<Transaction> page = Page.empty();
		when(transactionRepository.findByAccount_AccountNumber(eq("ACC1"), any(Pageable.class))).thenReturn(page);

		Page<TransactionResponse> out = service.listByAccount("ACC1", null, null, PageRequest.of(0, 10));

		assertThat(out.getTotalElements()).isEqualTo(0);
		verify(transactionRepository).findByAccount_AccountNumber(eq("ACC1"), any(Pageable.class));
		verify(transactionRepository, never()).findByAccount_AccountNumberAndTrxDateBetween(any(), any(), any(), any());
	}

	@Test
	void search_callsFindAllWithSpecificationAndMaps() {
		Transaction tx = mock(Transaction.class);
		TransactionResponse tr = mock(TransactionResponse.class);
		Page<Transaction> page = new PageImpl<>(List.of(tx));

		when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
		when(transactionMapper.toResponse(tx)).thenReturn(tr);

		Pageable pageable = PageRequest.of(0, 5);

		Page<TransactionResponse> out = service.search(
				"C1",
				List.of("A1", "A2"),
				"rent",
				LocalDate.of(2025, 12, 1),
				LocalDate.of(2025, 12, 31),
				pageable
		);

		assertThat(out.getContent()).containsExactly(tr);

		ArgumentCaptor<Specification<Transaction>> specCaptor = ArgumentCaptor.forClass(Specification.class);
		verify(transactionRepository).findAll(specCaptor.capture(), eq(pageable));
		assertThat(specCaptor.getValue()).isNotNull(); // repository supports Specification+Pageable :contentReference[oaicite:3]{index=3}
	}

	@Test
	void getById_notFound_throwsNotFound() {
		when(transactionRepository.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getById(1L))
				.isInstanceOf(NotFoundException.class)
				.hasMessageContaining("Transaction not found id=1");
	}

	@Test
	void getById_found_returnsMapped() {
		Transaction tx = mock(Transaction.class);
		TransactionResponse tr = mock(TransactionResponse.class);

		when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
		when(transactionMapper.toResponse(tx)).thenReturn(tr);

		assertThat(service.getById(1L)).isSameAs(tr);
	}

	@Test
	void updateDescription_missingIfMatch_throws412Required() {
		ResponseStatusException ex = catchThrowableOfType(
				() -> service.updateDescription(1L, "x", " "),
				ResponseStatusException.class
		);

		assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
		verify(transactionRepository, never()).save(any());
	}

	@Test
	void updateDescription_invalidIfMatch_throws400() {
		Transaction tx = mock(Transaction.class);
		when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
		when(tx.getVersion()).thenReturn(1L);

		ResponseStatusException ex = catchThrowableOfType(
				() -> service.updateDescription(1L, "x", "\"abc\""),
				ResponseStatusException.class
		);

		assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verify(transactionRepository, never()).save(any());
	}

	@Test
	void updateDescription_etagMismatch_throws412Failed() {
		Transaction tx = mock(Transaction.class);
		when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
		when(tx.getVersion()).thenReturn(2L);

		ResponseStatusException ex = catchThrowableOfType(
				() -> service.updateDescription(1L, "x", "\"1\""),
				ResponseStatusException.class
		);

		assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
		verify(transactionRepository, never()).save(any());
	}

	@Test
	void updateDescription_happyPath_savesAndMaps() {
		Transaction tx = mock(Transaction.class);
		Transaction saved = mock(Transaction.class);
		TransactionResponse tr = mock(TransactionResponse.class);

		when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
		when(tx.getVersion()).thenReturn(5L);
		when(transactionRepository.save(tx)).thenReturn(saved);
		when(transactionMapper.toResponse(saved)).thenReturn(tr);

		TransactionResponse out = service.updateDescription(1L, "new", "\"5\"");

		assertThat(out).isSameAs(tr);
		verify(tx).setDescription("new");
		verify(tx).setUpdatedAt(any());
		verify(transactionRepository).save(tx);
	}

	@Test
	void updateDescription_optimisticLockingFailure_throws412Failed() {
		Transaction tx = mock(Transaction.class);
		when(transactionRepository.findById(1L)).thenReturn(Optional.of(tx));
		when(tx.getVersion()).thenReturn(5L);
		when(transactionRepository.save(tx)).thenThrow(new OptimisticLockingFailureException("boom"));

		ResponseStatusException ex = catchThrowableOfType(
				() -> service.updateDescription(1L, "new", "\"5\""),
				ResponseStatusException.class
		);

		assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
	}

	@Test
	void dailySummary_calculatesTotalAndCount() {
		LocalDate date = LocalDate.of(2025, 12, 1);

		Transaction t1 = mock(Transaction.class);
		Transaction t2 = mock(Transaction.class);
		when(t1.getAmount()).thenReturn(new BigDecimal("10.00"));
		when(t2.getAmount()).thenReturn(new BigDecimal("5.50"));

		Page<Transaction> page = new PageImpl<>(List.of(t1, t2));
		when(transactionRepository.findByAccount_AccountNumberAndTrxDateBetween(
				eq("ACC1"), eq(date), eq(date), eq(Pageable.unpaged())
		)).thenReturn(page);

		DailySummaryResponse out = service.dailySummary("ACC1", date);

		assertThat(out.accountNumber()).isEqualTo("ACC1");
		assertThat(out.date()).isEqualTo(date);
		assertThat(out.totalAmount()).isEqualByComparingTo(new BigDecimal("15.50"));
		assertThat(out.transactionCount()).isEqualTo(2);
	}
}
