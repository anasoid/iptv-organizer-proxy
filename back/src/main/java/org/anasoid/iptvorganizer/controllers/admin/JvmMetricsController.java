package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.monitor.JvmMetricsEntry;
import org.anasoid.iptvorganizer.services.monitor.JvmMonitorService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/**
 * Admin REST endpoint exposing in-memory JVM and OS metrics.
 *
 * <p>{@code GET /api/jvm/metrics?startDate=yyyy-MM-ddTHH:mm:ss&endDate=yyyy-MM-ddTHH:mm:ss}
 *
 * <p>Both query parameters are optional. Dates are parsed as {@link LocalDateTime} (server-local
 * time, no timezone offset). The response list is ordered oldest-first so chart libraries receive
 * data in natural chronological order without client-side sorting.
 */
@Slf4j
@Path("/api/jvm/metrics")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class JvmMetricsController extends BaseController {
  @Inject JvmMonitorService jvmMonitorService;

  /**
   * Returns retained JVM metric snapshots, filtered by an optional date range.
   *
   * @param startDate ISO-8601 local date-time string ({@code yyyy-MM-ddTHH:mm:ss}), inclusive lower
   *     bound; omit to start from the oldest retained entry
   * @param endDate ISO-8601 local date-time string, inclusive upper bound; omit to include up to
   *     the most recent entry
   */
  @GET
  public Response getMetrics(
      @QueryParam("startDate") String startDate, @QueryParam("endDate") String endDate) {
    LocalDateTime start =
        (startDate != null && !startDate.isBlank()) ? LocalDateTime.parse(startDate) : null;
    LocalDateTime end =
        (endDate != null && !endDate.isBlank()) ? LocalDateTime.parse(endDate) : null;
    log.debug("JVM metrics request: start={} end={}", start, end);
    List<JvmMetricsEntry> result = jvmMonitorService.getMetrics(start, end);
    return ResponseUtils.ok(result);
  }
}
