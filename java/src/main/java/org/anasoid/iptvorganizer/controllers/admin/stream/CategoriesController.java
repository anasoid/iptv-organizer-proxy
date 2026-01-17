package org.anasoid.iptvorganizer.controllers.admin.stream;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.controllers.admin.BaseController;
import org.anasoid.iptvorganizer.dto.CategoryDTO;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.services.stream.CategoryService;

/** Categories controller */
@Path("/api/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class CategoriesController extends BaseController {

  @Inject CategoryService categoryService;

  /**
   * Get categories with filters GET
   * /api/categories?source_id=&page=1&limit=20&search=&category_type=
   */
  @GET
  public Response getCategories(
      @QueryParam("source_id") Long sourceId,
      @QueryParam("category_type") String categoryType,
      @QueryParam("search") String search,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (sourceId == null) {
      return Response.ok(ApiResponse.error("source_id is required")).build();
    }

    try {
      var categories =
          categoryService
              .findBySourceIdFiltered(sourceId, categoryType, search, page, limit)
              .stream()
              .map(CategoryDTO::fromEntity)
              .collect(Collectors.toList());
      long total = categoryService.countBySourceIdFiltered(sourceId, categoryType, search);
      return Response.ok(
              ApiResponse.successWithPagination(categories, PaginationMeta.of(page, limit, total)))
          .build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to fetch categories: " + ex.getMessage()))
          .build();
    }
  }

  /** Get category by ID GET /api/categories/:id?source_id= */
  @GET
  @Path("/{id}")
  public Response getCategory(@PathParam("id") Long id) {
    try {
      var cat = categoryService.getById(id);
      if (cat != null) {
        return Response.ok(ApiResponse.success(CategoryDTO.fromEntity(cat))).build();
      } else {
        return Response.ok(ApiResponse.error("Category not found")).build();
      }
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Category not found")).build();
    }
  }

  /** Update allow-deny status PATCH /api/categories/:id/allow-deny */
  @PATCH
  @Path("/{id}/allow-deny")
  public Response updateAllowDeny(@PathParam("id") Long id, java.util.Map<String, String> request) {
    try {
      var cat = categoryService.getById(id);
      if (cat == null) {
        return Response.ok(ApiResponse.error("Category not found")).build();
      }

      if (request != null && request.get("allowDeny") != null) {
        cat.setAllowDeny(request.get("allowDeny"));
        categoryService.update(cat);
      }
      return Response.ok(ApiResponse.success(CategoryDTO.fromEntity(cat))).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to update category: " + ex.getMessage()))
          .build();
    }
  }
}
