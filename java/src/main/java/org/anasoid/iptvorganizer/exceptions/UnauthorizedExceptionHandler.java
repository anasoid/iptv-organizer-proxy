package org.anasoid.iptvorganizer.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;

@Provider
public class UnauthorizedExceptionHandler implements ExceptionMapper<UnauthorizedException> {
  @Override
  public Response toResponse(UnauthorizedException ex) {
    return Response.status(Response.Status.UNAUTHORIZED)
        .entity(ApiResponse.error(ex.getMessage()))
        .build();
  }
}
