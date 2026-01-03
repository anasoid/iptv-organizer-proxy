package org.anasoid.iptvorganizer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard API response wrapper for all endpoints
 * Matches the format expected by the React admin frontend
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private PaginationMeta pagination;

    /**
     * Create a success response with data
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .build();
    }

    /**
     * Create a success response with data and message
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .message(message)
            .build();
    }

    /**
     * Create a success response with paginated data
     */
    public static <T> ApiResponse<T> successWithPagination(T data, PaginationMeta pagination) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .pagination(pagination)
            .build();
    }

    /**
     * Create an error response
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .build();
    }
}
