package org.anasoid.iptvorganizer.exceptions;

import jakarta.ws.rs.core.Response;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Global exception handler for all API exceptions */
public class GlobalExceptionHandler {

  @ServerExceptionMapper
  public Response handleNotFoundException(NotFoundException ex) {
    return Response.status(Response.Status.NOT_FOUND)
        .entity(ApiResponse.error(ex.getMessage()))
        .build();
  }

  @ServerExceptionMapper
  public Response handleValidationException(ValidationException ex) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(ApiResponse.error(ex.getMessage()))
        .build();
  }

  @ServerExceptionMapper
  public Response handleUnauthorizedException(UnauthorizedException ex) {
    return Response.status(Response.Status.UNAUTHORIZED)
        .entity(ApiResponse.error(ex.getMessage()))
        .build();
  }

  @ServerExceptionMapper
  public Response handleGeneralException(Exception ex) {
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(ApiResponse.error("An unexpected error occurred"))
        .build();
  }
}
