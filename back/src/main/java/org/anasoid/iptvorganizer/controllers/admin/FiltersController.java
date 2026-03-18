package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.entity.Filter;
import org.anasoid.iptvorganizer.services.FilterService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Filters controller CRUD operations for filters */
@Path("/api/filters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class FiltersController extends BaseController {

  @Inject FilterService filterService;

  /** Get all filters with pagination GET /api/filters?page=1&limit=20 */
  @GET
  public Response getAllFilters(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (page < 1 || limit < 1) {
      throw new ValidationException("Page and limit must be greater than 0");
    }

    var filters = filterService.getAllPaged(page, limit);
    long total = filterService.count();
    var pagination = PaginationMeta.of(page, limit, total);
    return ResponseUtils.okWithPagination(filters, pagination);
  }

  /** Get filter by ID GET /api/filters/:id */
  @GET
  @Path("/{id}")
  public Response getFilter(@PathParam("id") Long id) {
    Filter filter = filterService.getById(id);
    if (filter == null) {
      throw new NotFoundException("Filter not found with ID: " + id);
    }
    return ResponseUtils.ok(filter);
  }

  /** Create filter POST /api/filters */
  @POST
  public Response createFilter(Filter request) {
    if (request.getName() == null || request.getName().isBlank()) {
      throw new ValidationException("Name is required");
    }

    // Set defaults for new filter
    if (request.getUseSourceFilter() == null) {
      request.setUseSourceFilter(false);
    }

    Filter savedFilter = filterService.save(request);
    return ResponseUtils.created(savedFilter);
  }

  /** Update filter PUT /api/filters/:id */
  @PUT
  @Path("/{id}")
  public Response updateFilter(@PathParam("id") Long id, Filter request) {
    Filter filter = filterService.getById(id);
    if (filter == null) {
      throw new NotFoundException("Filter not found with ID: " + id);
    }

    // Merge non-null fields from request
    if (request.getName() != null) filter.setName(request.getName());
    if (request.getDescription() != null) filter.setDescription(request.getDescription());
    if (request.getFilterConfig() != null) filter.setFilterConfig(request.getFilterConfig());
    if (request.getUseSourceFilter() != null)
      filter.setUseSourceFilter(request.getUseSourceFilter());
    if (request.getFavoris() != null) filter.setFavoris(request.getFavoris());

    filterService.update(filter);
    return ResponseUtils.ok(filter);
  }

  /** Delete filter DELETE /api/filters/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteFilter(@PathParam("id") Long id) {
    filterService.delete(id);
    return ResponseUtils.okMessage("Filter deleted successfully");
  }

  /** Toggle use source filter flag PATCH /api/filters/:id/use-source-filter */
  @PATCH
  @Path("/{id}/use-source-filter")
  public Response toggleUseSourceFilter(
      @PathParam("id") Long id, java.util.Map<String, Boolean> request) {
    Filter filter = filterService.getById(id);
    if (filter == null) {
      throw new NotFoundException("Filter not found with ID: " + id);
    }

    if (request != null && request.get("useSourceFilter") != null) {
      filter.setUseSourceFilter(request.get("useSourceFilter"));
      filterService.update(filter);
    }
    return ResponseUtils.ok(filter);
  }
}
