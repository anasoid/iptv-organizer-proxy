package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.entity.Proxy;
import org.anasoid.iptvorganizer.services.ProxyService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Proxies controller for CRUD operations on proxy configurations */
@Path("/api/proxies")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProxiesController extends BaseController {

  @Inject ProxyService proxyService;

  /** Get all proxies with pagination GET /api/proxies?page=1&limit=20 */
  @GET
  public Response getAllProxies(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (page < 1 || limit < 1) {
      throw new ValidationException("Page and limit must be greater than 0");
    }

    var proxies = proxyService.getAllPaged(page, limit);
    long total = proxyService.count();
    var pagination = PaginationMeta.of(page, limit, total);
    return ResponseUtils.okWithPagination(proxies, pagination);
  }

  /** Get proxy by ID GET /api/proxies/:id */
  @GET
  @Path("/{id}")
  public Response getProxy(@PathParam("id") Long id) {
    Proxy proxy = proxyService.getById(id);
    if (proxy == null) {
      throw new NotFoundException("Proxy not found with ID: " + id);
    }
    return ResponseUtils.ok(proxy);
  }

  /** Create proxy POST /api/proxies */
  @POST
  public Response createProxy(Proxy request) {
    // Set defaults
    Long id = proxyService.create(request);
    request.setId(id);
    return ResponseUtils.created(request);
  }

  /** Update proxy PUT /api/proxies/:id */
  @PUT
  @Path("/{id}")
  public Response updateProxy(@PathParam("id") Long id, Proxy request) {
    Proxy proxy = proxyService.getById(id);
    if (proxy == null) {
      throw new NotFoundException("Proxy not found with ID: " + id);
    }

    // Update basic fields
    if (request.getName() != null && !request.getName().isBlank()) {
      // Check if new name already exists (and it's not the same as current name)
      if (!request.getName().equals(proxy.getName())
          && proxyService.nameExists(request.getName())) {
        throw new ValidationException("Proxy name already exists");
      }
      proxy.setName(request.getName());
    }

    if (request.getDescription() != null) {
      proxy.setDescription(request.getDescription());
    }

    if (request.getProxyUrl() != null) {
      proxy.setProxyUrl(request.getProxyUrl());
    }

    if (request.getProxyHost() != null) {
      proxy.setProxyHost(request.getProxyHost());
    }

    if (request.getProxyPort() != null) {
      proxy.setProxyPort(request.getProxyPort());
    }

    if (request.getProxyType() != null) {
      proxy.setProxyType(request.getProxyType());
    }

    if (request.getProxyUsername() != null) {
      proxy.setProxyUsername(request.getProxyUsername());
    }

    if (request.getProxyPassword() != null) {
      proxy.setProxyPassword(request.getProxyPassword());
    }

    if (request.getTimeout() != null) {
      proxy.setTimeout(request.getTimeout());
    }

    if (request.getMaxRetries() != null) {
      proxy.setMaxRetries(request.getMaxRetries());
    }

    proxyService.update(proxy);
    return ResponseUtils.ok(proxy);
  }

  /** Delete proxy DELETE /api/proxies/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteProxy(@PathParam("id") Long id) {
    proxyService.delete(id);
    return ResponseUtils.okMessage("Proxy deleted successfully");
  }
}
