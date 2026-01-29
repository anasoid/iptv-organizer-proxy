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
import org.anasoid.iptvorganizer.controllers.admin.BaseController;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Categories controller */
@Path("/api/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class CategoriesController extends BaseController {

  @Inject CategoryService categoryService;

  /**
   * Get categories with filters GET /api/categories?sourceId=&page=1&limit=20&search=&categoryType=
   */
  @GET
  public Response getCategories(
      @QueryParam("sourceId") Long sourceId,
      @QueryParam("categoryType") String categoryType,
      @QueryParam("search") String search,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (sourceId == null) {
      throw new ValidationException("sourceId is required");
    }

    var categories =
        categoryService.findBySourceIdFiltered(sourceId, categoryType, search, page, limit);
    long total = categoryService.countBySourceIdFiltered(sourceId, categoryType, search);
    return ResponseUtils.okWithPagination(categories, PaginationMeta.of(page, limit, total));
  }

  /** Get category by ID GET /api/categories/:id?source_id= */
  @GET
  @Path("/{id}")
  public Response getCategory(@PathParam("id") Long id) {
    var cat = categoryService.getById(id);
    if (cat == null) {
      throw new NotFoundException("Category not found with ID: " + id);
    }
    return ResponseUtils.ok(cat);
  }

  /** Update allow-deny status PATCH /api/categories/:id/allow-deny */
  @PATCH
  @Path("/{id}/allow-deny")
  public Response updateAllowDeny(@PathParam("id") Long id, java.util.Map<String, String> request) {
    var cat = categoryService.getById(id);
    if (cat == null) {
      throw new NotFoundException("Category not found with ID: " + id);
    }

    if (request != null && request.get("allowDeny") != null) {
      cat.setAllowDeny(BaseStream.AllowDenyStatus.fromValue((String) request.get("allowDeny")));
      categoryService.update(cat);
    }
    return ResponseUtils.ok(cat);
  }
}
