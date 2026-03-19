package org.anasoid.iptvorganizer.exceptions;

import io.quarkus.runtime.LaunchMode;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.ErrorDetails;

@Provider
@Slf4j
public class GeneralExceptionHandler implements ExceptionMapper<Exception> {
  private static final int MAX_STACK_TRACE_FRAMES = 20;

  @Context private UriInfo uriInfo;

  @Override
  public Response toResponse(Exception ex) {
    Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
    String errorCode = "INTERNAL_ERROR";
    int statusCode = 500;

    // JAX-RS standard exceptions (e.g. jakarta.ws.rs.NotFoundException thrown by RESTEasy when no
    // route matches) already carry the correct HTTP status — use it directly rather than
    // defaulting to 500.  This also suppresses ERROR-level noise from browser probe requests
    // like Chrome's /.well-known/appspecific/com.chrome.devtools.json.
    if (ex instanceof WebApplicationException wae) {
      statusCode = wae.getResponse().getStatus();
      status = Response.Status.fromStatusCode(statusCode);
      errorCode = statusCode == 404 ? "NOT_FOUND" : "CLIENT_ERROR";
    } else if (ex instanceof ValidationException) {
      status = Response.Status.BAD_REQUEST;
      errorCode = "VALIDATION_ERROR";
      statusCode = 400;
    } else if (ex instanceof UnauthorizedException) {
      status = Response.Status.UNAUTHORIZED;
      errorCode = "UNAUTHORIZED";
      statusCode = 401;
    } else if (ex instanceof ForbiddenException) {
      status = Response.Status.FORBIDDEN;
      errorCode = "FORBIDDEN";
      statusCode = 403;
    } else if (ex instanceof NotFoundException) {
      status = Response.Status.NOT_FOUND;
      errorCode = "NOT_FOUND";
      statusCode = 404;
    } else if (ex instanceof FilterException) {
      errorCode = "FILTER_ERROR";
    } else if (ex instanceof StreamingException) {
      status = Response.Status.BAD_GATEWAY;
      errorCode = "STREAMING_ERROR";
      statusCode = 502;
    }

    // Extract stack trace if enabled (dev/test mode only)
    List<String> stackTrace = null;
    boolean isDevMode = LaunchMode.current().isDevOrTest();
    if (isDevMode) {
      stackTrace = extractStackTrace(ex);
    }

    // Build error details
    ErrorDetails errorDetails =
        ErrorDetails.builder()
            .message(ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred")
            .errorCode(errorCode)
            .status(statusCode)
            .exceptionType(ex.getClass().getName())
            .stackTrace(stackTrace)
            .timestamp(LocalDateTime.now())
            .path(uriInfo != null ? uriInfo.getAbsolutePath().toString() : null)
            .build();

    // Log appropriately based on status
    if (statusCode >= 500) {
      log.error("Internal server error ( {} ): {}", errorCode, ex.getMessage(), ex);
    } else if (statusCode == 404) {
      // 404s are noisy (browser probes, missing favicons, etc.) — DEBUG is enough
      log.debug("Not found ( {} ): {}", errorCode, ex.getMessage());
    } else {
      log.warn("Client error ( {} ): {}", errorCode, ex.getMessage());
    }

    return Response.status(status).entity(ApiResponse.error(errorDetails)).build();
  }

  private List<String> extractStackTrace(Throwable ex) {
    List<String> stackTrace = new ArrayList<>();
    StackTraceElement[] elements = ex.getStackTrace();

    int framesToInclude = Math.min(elements.length, MAX_STACK_TRACE_FRAMES);
    for (int i = 0; i < framesToInclude; i++) {
      StackTraceElement element = elements[i];
      stackTrace.add(
          String.format(
              "%s.%s(%s:%d)",
              element.getClassName(),
              element.getMethodName(),
              element.getFileName(),
              element.getLineNumber()));
    }

    // Add indicator if truncated
    if (elements.length > MAX_STACK_TRACE_FRAMES) {
      stackTrace.add(String.format("... %d more frames", elements.length - MAX_STACK_TRACE_FRAMES));
    }

    return stackTrace;
  }
}
