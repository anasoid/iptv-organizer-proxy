package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.dto.FilterDTO;
import org.anasoid.iptvorganizer.dto.request.CreateFilterRequest;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
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
      return ResponseUtils.badRequest("Page and limit must be greater than 0");
    }

    try {
      var filters =
          filterService.getAllPaged(page, limit).stream()
              .map(FilterDTO::fromEntity)
              .collect(Collectors.toList());
      long total = filterService.count();
      var pagination = PaginationMeta.of(page, limit, total);
      return ResponseUtils.okWithPagination(filters, pagination);
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to fetch filters: " + ex.getMessage());
    }
  }

  /** Get filter by ID GET /api/filters/:id */
  @GET
  @Path("/{id}")
  public Response getFilter(@PathParam("id") Long id) {
    try {
      Filter filter = filterService.getById(id);
      if (filter != null) {
        return ResponseUtils.ok(FilterDTO.fromEntity(filter));
      } else {
        return ResponseUtils.notFound("Filter not found");
      }
    } catch (Exception ex) {
      return ResponseUtils.notFound("Filter not found");
    }
  }

  /** Create filter POST /api/filters */
  @POST
  public Response createFilter(CreateFilterRequest request) {
    if (request.getName() == null || request.getName().isBlank()) {
      return ResponseUtils.badRequest("Name is required");
    }

    try {
      Filter filter =
          Filter.builder()
              .name(request.getName())
              .description(request.getDescription())
              .filterConfig(request.getFilterConfig())
              .useSourceFilter(
                  request.getUseSourceFilter() != null ? request.getUseSourceFilter() : false)
              .favoris(request.getFavoris())
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();

      Filter savedFilter = filterService.save(filter);
      return ResponseUtils.created(FilterDTO.fromEntity(savedFilter));
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to create filter: " + ex.getMessage());
    }
  }

  /** Update filter PUT /api/filters/:id */
  @PUT
  @Path("/{id}")
  public Response updateFilter(@PathParam("id") Long id, CreateFilterRequest request) {
    try {
      Filter filter = filterService.getById(id);
      if (filter == null) {
        return ResponseUtils.notFound("Filter not found");
      }

      if (request.getName() != null) filter.setName(request.getName());
      if (request.getDescription() != null) filter.setDescription(request.getDescription());
      if (request.getFilterConfig() != null) filter.setFilterConfig(request.getFilterConfig());
      if (request.getUseSourceFilter() != null)
        filter.setUseSourceFilter(request.getUseSourceFilter());
      if (request.getFavoris() != null) filter.setFavoris(request.getFavoris());
      filter.setUpdatedAt(LocalDateTime.now());

      filterService.update(filter);
      return ResponseUtils.ok(FilterDTO.fromEntity(filter));
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to update filter: " + ex.getMessage());
    }
  }

  /** Delete filter DELETE /api/filters/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteFilter(@PathParam("id") Long id) {
    try {
      filterService.delete(id);
      return ResponseUtils.okMessage("Filter deleted successfully");
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to delete filter: " + ex.getMessage());
    }
  }

  /** Toggle use source filter flag PATCH /api/filters/:id/use-source-filter */
  @PATCH
  @Path("/{id}/use-source-filter")
  public Response toggleUseSourceFilter(
      @PathParam("id") Long id, java.util.Map<String, Boolean> request) {
    try {
      Filter filter = filterService.getById(id);
      if (filter == null) {
        return ResponseUtils.notFound("Filter not found");
      }

      if (request != null && request.get("use_source_filter") != null) {
        filter.setUseSourceFilter(request.get("use_source_filter"));
        filter.setUpdatedAt(LocalDateTime.now());
        filterService.update(filter);
      }
      return ResponseUtils.ok(FilterDTO.fromEntity(filter));
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to update filter: " + ex.getMessage());
    }
  }
}
