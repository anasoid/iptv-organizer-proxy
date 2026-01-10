package org.anasoid.iptvorganizer.controllers.admin;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import org.anasoid.iptvorganizer.dto.FilterDTO;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.models.Filter;
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
  public Uni<?> getAllFilters(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (page < 1 || limit < 1) {
      return Uni.createFrom().item(ApiResponse.error("Page and limit must be greater than 0"));
    }

    return Uni.combine()
        .all()
        .unis(
            filterService.getAllPaged(page, limit).map(FilterDTO::fromEntity).collect().asList(),
            filterService.count())
        .asTuple()
        .map(
            tuple -> {
              var filters = tuple.getItem1();
              long total = tuple.getItem2();
              var pagination = PaginationMeta.of(page, limit, total);
              return ApiResponse.successWithPagination(filters, pagination);
            })
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Failed to fetch filters: " + ex.getMessage()));
  }

  /** Get filter by ID GET /api/filters/:id */
  @GET
  @Path("/{id}")
  public Uni<?> getFilter(@PathParam("id") Long id) {
    return filterService
        .getById(id)
        .map(
            filter ->
                filter != null
                    ? ApiResponse.success(FilterDTO.fromEntity(filter))
                    : ApiResponse.error("Filter not found"))
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Filter not found"));
  }

  /** Create filter POST /api/filters */
  @POST
  public Uni<?> createFilter(Filter request) {
    if (request.getName() == null || request.getName().isBlank()) {
      return Uni.createFrom().item(ApiResponse.error("Name is required"));
    }

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

    return filterService
        .save(filter)
        .map(f -> ApiResponse.success(FilterDTO.fromEntity(f)))
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Failed to create filter: " + ex.getMessage()));
  }

  /** Update filter PUT /api/filters/:id */
  @PUT
  @Path("/{id}")
  public Uni<?> updateFilter(@PathParam("id") Long id, Filter request) {
    return filterService
        .getById(id)
        .flatMap(
            filter -> {
              if (request.getName() != null) filter.setName(request.getName());
              if (request.getDescription() != null) filter.setDescription(request.getDescription());
              if (request.getFilterConfig() != null)
                filter.setFilterConfig(request.getFilterConfig());
              if (request.getUseSourceFilter() != null)
                filter.setUseSourceFilter(request.getUseSourceFilter());
              if (request.getFavoris() != null) filter.setFavoris(request.getFavoris());
              filter.setUpdatedAt(LocalDateTime.now());

              return filterService.update(filter).map(v -> filter);
            })
        .map(filter -> ApiResponse.success(FilterDTO.fromEntity(filter)))
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Failed to update filter: " + ex.getMessage()));
  }

  /** Delete filter DELETE /api/filters/:id */
  @DELETE
  @Path("/{id}")
  public Uni<?> deleteFilter(@PathParam("id") Long id) {
    return filterService
        .delete(id)
        .map(v -> ApiResponse.success("Filter deleted successfully"))
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Failed to delete filter: " + ex.getMessage()));
  }

  /** Toggle use source filter flag PATCH /api/filters/:id/use-source-filter */
  @PATCH
  @Path("/{id}/use-source-filter")
  public Uni<?> toggleUseSourceFilter(
      @PathParam("id") Long id, java.util.Map<String, Boolean> request) {
    return filterService
        .getById(id)
        .flatMap(
            filter -> {
              if (request != null && request.get("useSourceFilter") != null) {
                filter.setUseSourceFilter(request.get("useSourceFilter"));
                filter.setUpdatedAt(LocalDateTime.now());
                return filterService.update(filter).map(v -> filter);
              }
              return Uni.createFrom().item(filter);
            })
        .map(filter -> ApiResponse.success(FilterDTO.fromEntity(filter)))
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Failed to update filter: " + ex.getMessage()));
  }
}
