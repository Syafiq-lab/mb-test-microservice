package mb.be.account.mapper;

import mb.be.account.domain.Account;
import mb.be.account.dto.AccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

	// Flatten owner.customerId into customerId in the response DTO
	@Mapping(target = "customerId", source = "owner.customerId")
	AccountResponse toResponse(Account entity);
}
