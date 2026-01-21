package org.anasoid.iptvorganizer.utils;

import jakarta.ws.rs.core.Response;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;

/** Utility class for creating standard REST API responses with proper HTTP status codes */
public class ResponseUtils {

  /**
   * 200 OK - Success with data
   *
   * @param data The response data
   * @return Response with 200 status and success ApiResponse
   */
  public static Response ok(Object data) {
    return Response.ok(ApiResponse.success(data)).build();
  }

  /**
   * 200 OK - Success with message
   *
   * @param message The success message
   * @return Response with 200 status and success ApiResponse
   */
  public static Response okMessage(String message) {
    return Response.ok(ApiResponse.success(message)).build();
  }

  /**
   * 200 OK - Success with data and pagination
   *
   * @param data The response data
   * @param pagination The pagination metadata
   * @return Response with 200 status and paginated success ApiResponse
   */
  public static Response okWithPagination(Object data, PaginationMeta pagination) {
    return Response.ok(ApiResponse.successWithPagination(data, pagination)).build();
  }

  /**
   * 201 Created - Resource created successfully
   *
   * @param data The created resource data
   * @return Response with 201 status and success ApiResponse
   */
  public static Response created(Object data) {
    return Response.status(Response.Status.CREATED).entity(ApiResponse.success(data)).build();
  }

  /**
   * 400 Bad Request - Validation errors, invalid input
   *
   * @param message The error message
   * @return Response with 400 status and error ApiResponse
   */
  public static Response badRequest(String message) {
    return Response.status(Response.Status.BAD_REQUEST).entity(ApiResponse.error(message)).build();
  }

  /**
   * 404 Not Found - Resource doesn't exist
   *
   * @param message The error message
   * @return Response with 404 status and error ApiResponse
   */
  public static Response notFound(String message) {
    return Response.status(Response.Status.NOT_FOUND).entity(ApiResponse.error(message)).build();
  }

  /**
   * 500 Internal Server Error - Unexpected exceptions
   *
   * @param message The error message
   * @return Response with 500 status and error ApiResponse
   */
  public static Response serverError(String message) {
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(ApiResponse.error(message))
        .build();
  }

  /**
   * Handle exception with appropriate status code
   *
   * @param ex The exception
   * @param defaultMessage The default error message
   * @return Response with 500 status and error ApiResponse
   */
  public static Response handleException(Exception ex, String defaultMessage) {
    return serverError(defaultMessage + ": " + ex.getMessage());
  }
}
