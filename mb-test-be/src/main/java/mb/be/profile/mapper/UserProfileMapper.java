package mb.be.profile.mapper;

import mb.be.profile.domain.UserProfile;
import mb.be.profile.dto.CreateUserProfileRequest;
import mb.be.profile.dto.UserProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {

	@Mapping(target = "id", ignore = true)
	UserProfile toEntity(CreateUserProfileRequest request);

	UserProfileResponse toResponse(UserProfile entity);
}
