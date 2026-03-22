package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.cache.CacheManager;
import org.anasoid.iptvorganizer.cache.CacheStat;
import org.anasoid.iptvorganizer.services.DatabaseMaintenanceService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

@Slf4j
@Path("/api/cache")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class CacheController extends BaseController {

  @Inject CacheManager cacheManager;
  @Inject DatabaseMaintenanceService databaseMaintenanceService;

  @GET
  @Path("/stats")
  public Response getCacheStats() {
    log.info("Getting all cache statistics");
    List<CacheStat> stats = cacheManager.getAllCacheStats();
    return ResponseUtils.ok(stats);
  }

  @POST
  @Path("/database/shrink")
  public Response shrinkDatabase() {
    if (!databaseMaintenanceService.isSQLiteDialect()) {
      return ResponseUtils.badRequest("Database shrink is currently supported only for SQLite");
    }

    try {
      log.info("Starting database shrink operation");
      DatabaseMaintenanceService.DatabaseShrinkResult result =
          databaseMaintenanceService.shrinkDatabase();
      return ResponseUtils.ok(result);
    } catch (Exception e) {
      log.error("Database shrink failed", e);
      return ResponseUtils.serverError("Failed to shrink database: " + e.getMessage());
    }
  }
}
