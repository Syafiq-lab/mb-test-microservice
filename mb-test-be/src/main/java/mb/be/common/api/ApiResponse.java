package mb.be.common.api;

import lombok.Builder;

@Builder
public record ApiResponse<T>(
		String status,
		String message,
		T data
) {
	public static <T> ApiResponse<T> success(String message, T data) {
		return ApiResponse.<T>builder()
				.status("SUCCESS")
				.message(message)
				.data(data)
				.build();
	}

	public static <T> ApiResponse<T> success(String message) {
		return ApiResponse.<T>builder()
				.status("SUCCESS")
				.message(message)
				.data(null)
				.build();
	}

	public static <T> ApiResponse<T> error(String message, T data) {
		return ApiResponse.<T>builder()
				.status("ERROR")
				.message(message)
				.data(data)
				.build();
	}

	public static ApiResponse<Void> error(String message) {
		return ApiResponse.<Void>builder()
				.status("ERROR")
				.message(message)
				.data(null)
				.build();
	}
}
