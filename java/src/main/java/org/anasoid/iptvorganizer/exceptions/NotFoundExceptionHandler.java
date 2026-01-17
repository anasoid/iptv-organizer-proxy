package org.anasoid.iptvorganizer.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;

@Provider
public class NotFoundExceptionHandler implements ExceptionMapper<NotFoundException> {
  @Override
  public Response toResponse(NotFoundException ex) {
    return Response.status(Response.Status.NOT_FOUND)
        .entity(ApiResponse.error(ex.getMessage()))
        .build();
  }
}
