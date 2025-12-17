package mb.be.profile.service;

import mb.be.profile.dto.CreateUserProfileRequest;
import mb.be.profile.dto.UserProfileResponse;
import org.springframework.transaction.annotation.Transactional;

public interface UserProfileService {

	@Transactional
	UserProfileResponse createUserProfile(CreateUserProfileRequest request);

	UserProfileResponse getUserProfile(String customerId);
}
