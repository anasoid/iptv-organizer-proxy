package org.anasoid.iptvorganizer.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;

@Provider
public class ValidationExceptionHandler implements ExceptionMapper<ValidationException> {
  @Override
  public Response toResponse(ValidationException ex) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(ApiResponse.error(ex.getMessage()))
        .build();
  }
}
