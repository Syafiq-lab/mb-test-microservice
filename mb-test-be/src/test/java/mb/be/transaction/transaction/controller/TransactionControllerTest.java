package mb.be.transaction.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mb.be.transaction.controller.TransactionController;
import mb.be.transaction.dto.CreateTransactionRequest;
import mb.be.transaction.dto.DailySummaryResponse;
import mb.be.transaction.dto.TransactionResponse;
import mb.be.transaction.dto.UpdateTransactionDescriptionRequest;
import mb.be.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false) // @WebMvcTest enables Spring Security by default :contentReference[oaicite:1]{index=1}
class TransactionControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;

	@MockBean
	TransactionService service;

	@Test
	void create_returns201_andCallsService() throws Exception {
		CreateTransactionRequest req = CreateTransactionRequest.builder()
				.accountNumber("1234567890")
				.amount(new BigDecimal("12.34"))
				.description("coffee")
				.trxDate(LocalDate.of(2025, 12, 1))
				.trxTime(LocalTime.of(10, 15, 30))
				.customerId("C001")
				.build();

		TransactionResponse created = mock(TransactionResponse.class);
		when(service.create(any(CreateTransactionRequest.class))).thenReturn(created);

		mockMvc.perform(post("/api/v1/transactions")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

		ArgumentCaptor<CreateTransactionRequest> captor = ArgumentCaptor.forClass(CreateTransactionRequest.class);
		verify(service).create(captor.capture());
		assertThat(captor.getValue().accountNumber()).isEqualTo("1234567890");
		assertThat(captor.getValue().customerId()).isEqualTo("C001");
	}

	@Test
	void create_invalidBody_returns400() throws Exception {
		// violates @NotBlank accountNumber, @NotNull trxDate/time, @NotBlank customerId, etc.
		CreateTransactionRequest bad = CreateTransactionRequest.builder()
				.accountNumber(" ")
				.amount(new BigDecimal("12.34"))
				.trxDate(null)
				.trxTime(null)
				.customerId("")
				.build();

		mockMvc.perform(post("/api/v1/transactions")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(bad)))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(service);
	}

	@Test
	void search_returns200_andPassesParamsToService() throws Exception {
		when(service.search(any(), any(), any(), any(), any(), any(Pageable.class)))
				.thenReturn(Page.empty());

		mockMvc.perform(get("/api/v1/transactions")
						.param("customerId", "C1")
						.param("accountNumber", "A1", "A2")
						.param("description", "rent")
						.param("fromDate", "2025-12-01")
						.param("toDate", "2025-12-31")
						.param("page", "0")
						.param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(service).search(
				eq("C1"),
				eq(List.of("A1", "A2")),
				eq("rent"),
				eq(LocalDate.of(2025, 12, 1)),
				eq(LocalDate.of(2025, 12, 31)),
				pageableCaptor.capture()
		);

		assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
	}

	@Test
	void getById_returns200_andSetsEtagFromVersion() throws Exception {
		TransactionResponse body = mock(TransactionResponse.class);
		when(body.version()).thenReturn(7L);
		when(service.getById(42L)).thenReturn(body);

		mockMvc.perform(get("/api/v1/transactions/{id}", 42))
				.andExpect(status().isOk())
				.andExpect(header().string("ETag", "\"7\"")) // ResponseEntity.eTag(...) formats quoted ETag
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

		verify(service).getById(42L);
	}

	@Test
	void updateDescription_returns200_andSetsEtag() throws Exception {
		UpdateTransactionDescriptionRequest req = new UpdateTransactionDescriptionRequest("new desc");

		TransactionResponse updated = mock(TransactionResponse.class);
		when(updated.version()).thenReturn(10L);
		when(service.updateDescription(eq(9L), eq("new desc"), eq("\"10\""))).thenReturn(updated);

		mockMvc.perform(patch("/api/v1/transactions/{id}", 9)
						.header("If-Match", "\"10\"")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(req)))
				.andExpect(status().isOk())
				.andExpect(header().string("ETag", "\"10\""))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

		verify(service).updateDescription(9L, "new desc", "\"10\"");
	}

	@Test
	void updateDescription_blankDescription_returns400() throws Exception {
		UpdateTransactionDescriptionRequest bad = new UpdateTransactionDescriptionRequest(" ");

		mockMvc.perform(patch("/api/v1/transactions/{id}", 9)
						.header("If-Match", "\"1\"")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(bad)))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(service);
	}

	@Test
	void listByAccount_returns200_andCallsService() throws Exception {
		when(service.listByAccount(eq("ACC1"), any(), any(), any(Pageable.class)))
				.thenReturn(Page.empty());

		mockMvc.perform(get("/api/v1/accounts/{accountNumber}/transactions", "ACC1")
						.param("fromDate", "2025-12-01")
						.param("toDate", "2025-12-31")
						.param("page", "1")
						.param("size", "20"))
				.andExpect(status().isOk());

		verify(service).listByAccount(
				eq("ACC1"),
				eq(LocalDate.of(2025, 12, 1)),
				eq(LocalDate.of(2025, 12, 31)),
				any(Pageable.class)
		);
	}

	@Test
	void dailySummary_returns200_andCallsService() throws Exception {
		DailySummaryResponse summary = DailySummaryResponse.builder()
				.accountNumber("ACC1")
				.date(LocalDate.of(2025, 12, 1))
				.totalAmount(new BigDecimal("0"))
				.transactionCount(0)
				.build();

		when(service.dailySummary("ACC1", LocalDate.of(2025, 12, 1))).thenReturn(summary);

		mockMvc.perform(get("/api/v1/accounts/{accountNumber}/transactions/summary/daily", "ACC1")
						.param("date", "2025-12-01"))
				.andExpect(status().isOk());

		verify(service).dailySummary("ACC1", LocalDate.of(2025, 12, 1));
	}
}
