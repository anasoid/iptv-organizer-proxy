package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.cache.CacheManager;
import org.anasoid.iptvorganizer.cache.CacheStat;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;

@Slf4j
@Path("/api/cache")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class CacheController extends BaseController {

  @Inject CacheManager cacheManager;

  @GET
  @Path("/stats")
  public Response getCacheStats() {
    log.info("Getting all cache statistics");
    List<CacheStat> stats = cacheManager.getAllCacheStats();
    return Response.ok(ApiResponse.success(stats)).build();
  }
}
