package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.dto.FilterDTO;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.models.entity.Filter;
import org.anasoid.iptvorganizer.services.FilterService;

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
      return Response.ok(ApiResponse.error("Page and limit must be greater than 0")).build();
    }

    try {
      var filters =
          filterService.getAllPaged(page, limit).stream()
              .map(FilterDTO::fromEntity)
              .collect(Collectors.toList());
      long total = filterService.count();
      var pagination = PaginationMeta.of(page, limit, total);
      return Response.ok(ApiResponse.successWithPagination(filters, pagination)).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to fetch filters: " + ex.getMessage())).build();
    }
  }

  /** Get filter by ID GET /api/filters/:id */
  @GET
  @Path("/{id}")
  public Response getFilter(@PathParam("id") Long id) {
    try {
      Filter filter = filterService.getById(id);
      if (filter != null) {
        return Response.ok(ApiResponse.success(FilterDTO.fromEntity(filter))).build();
      } else {
        return Response.ok(ApiResponse.error("Filter not found")).build();
      }
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Filter not found")).build();
    }
  }

  /** Create filter POST /api/filters */
  @POST
  public Response createFilter(Filter request) {
    if (request.getName() == null || request.getName().isBlank()) {
      return Response.ok(ApiResponse.error("Name is required")).build();
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
      return Response.ok(ApiResponse.success(FilterDTO.fromEntity(savedFilter))).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to create filter: " + ex.getMessage())).build();
    }
  }

  /** Update filter PUT /api/filters/:id */
  @PUT
  @Path("/{id}")
  public Response updateFilter(@PathParam("id") Long id, Filter request) {
    try {
      Filter filter = filterService.getById(id);
      if (filter == null) {
        return Response.ok(ApiResponse.error("Filter not found")).build();
      }

      if (request.getName() != null) filter.setName(request.getName());
      if (request.getDescription() != null) filter.setDescription(request.getDescription());
      if (request.getFilterConfig() != null) filter.setFilterConfig(request.getFilterConfig());
      if (request.getUseSourceFilter() != null)
        filter.setUseSourceFilter(request.getUseSourceFilter());
      if (request.getFavoris() != null) filter.setFavoris(request.getFavoris());
      filter.setUpdatedAt(LocalDateTime.now());

      filterService.update(filter);
      return Response.ok(ApiResponse.success(FilterDTO.fromEntity(filter))).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to update filter: " + ex.getMessage())).build();
    }
  }

  /** Delete filter DELETE /api/filters/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteFilter(@PathParam("id") Long id) {
    try {
      filterService.delete(id);
      return Response.ok(ApiResponse.success("Filter deleted successfully")).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to delete filter: " + ex.getMessage())).build();
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
        return Response.ok(ApiResponse.error("Filter not found")).build();
      }

      if (request != null && request.get("useSourceFilter") != null) {
        filter.setUseSourceFilter(request.get("useSourceFilter"));
        filter.setUpdatedAt(LocalDateTime.now());
        filterService.update(filter);
      }
      return Response.ok(ApiResponse.success(FilterDTO.fromEntity(filter))).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to update filter: " + ex.getMessage())).build();
    }
  }
}
