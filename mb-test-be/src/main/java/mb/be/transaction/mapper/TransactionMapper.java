package mb.be.transaction.mapper;

import mb.be.account.domain.Account;
import mb.be.transaction.domain.Transaction;
import mb.be.transaction.dto.CreateTransactionRequest;
import mb.be.transaction.dto.TransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

	@Mapping(target = "id", ignore = true)
	@Mapping(target = "account", source = "account")
	@Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
	@Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
	Transaction toEntity(CreateTransactionRequest request, Account account);

	@Mapping(target = "accountNumber", source = "account.accountNumber")
	TransactionResponse toResponse(Transaction entity);
}
