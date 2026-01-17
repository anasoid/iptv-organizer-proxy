package org.anasoid.iptvorganizer.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;

@Provider
public class GeneralExceptionHandler implements ExceptionMapper<Exception> {
  @Override
  public Response toResponse(Exception ex) {
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(ApiResponse.error("An unexpected error occurred"))
        .build();
  }
}
