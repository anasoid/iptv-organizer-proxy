package org.anasoid.iptvorganizer.controllers.admin.stream;

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
import java.util.Locale;
import java.util.Set;
import org.anasoid.iptvorganizer.controllers.admin.BaseController;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.entity.stream.AllowDenyStatus;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Categories controller */
@Path("/api/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CategoriesController extends BaseController {

  private static final Set<String> VALID_CATEGORY_TYPES = Set.of("live", "vod", "series");
  private static final Set<String> VALID_ALLOW_DENY_FILTERS =
      Set.of("all", "allow", "deny", "default");
  private static final Set<String> VALID_BLACKLIST_FILTERS =
      Set.of("all", "default", "hidden", "visible", "force_hidden");

  @Inject CategoryService categoryService;

  /**
   * Get categories with filters GET /api/categories?sourceId=&page=1&limit=20&search=&categoryType=
   */
  @GET
  public Response getCategories(
      @QueryParam("sourceId") Long sourceId,
      @QueryParam("categoryType") String categoryType,
      @QueryParam("allowDenyFilter") String allowDenyFilter,
      @QueryParam("blackListFilter") String blackListFilter,
      @QueryParam("search") String search,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (sourceId == null) {
      throw new ValidationException("sourceId is required");
    }

    String normalizedCategoryType = normalizeFilter(categoryType);
    String normalizedAllowDenyFilter = normalizeFilter(allowDenyFilter);
    String normalizedBlackListFilter = normalizeFilter(blackListFilter);

    validateFilter(normalizedCategoryType, VALID_CATEGORY_TYPES, "categoryType");
    validateFilter(normalizedAllowDenyFilter, VALID_ALLOW_DENY_FILTERS, "allowDenyFilter");
    validateFilter(normalizedBlackListFilter, VALID_BLACKLIST_FILTERS, "blackListFilter");

    if ("all".equals(normalizedAllowDenyFilter)) {
      normalizedAllowDenyFilter = null;
    }

    if ("all".equals(normalizedBlackListFilter)) {
      normalizedBlackListFilter = null;
    }

    var categories =
        categoryService.findBySourceIdFiltered(
            sourceId,
            normalizedCategoryType,
            search,
            normalizedAllowDenyFilter,
            normalizedBlackListFilter,
            page,
            limit);
    long total =
        categoryService.countBySourceIdFiltered(
            sourceId,
            normalizedCategoryType,
            search,
            normalizedAllowDenyFilter,
            normalizedBlackListFilter);
    return ResponseUtils.okWithPagination(categories, PaginationMeta.of(page, limit, total));
  }

  private String normalizeFilter(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.toLowerCase(Locale.ROOT);
  }

  private void validateFilter(String value, Set<String> validValues, String fieldName) {
    if (value != null && !validValues.contains(value)) {
      throw new ValidationException(
          "Invalid " + fieldName + ". Valid values: " + String.join(", ", validValues));
    }
  }

  /** Get category by ID GET /api/categories/:id */
  @GET
  @Path("/{id}")
  public Response getCategory(@PathParam("id") Long id) {
    var cat = categoryService.getById(id);
    if (cat == null) {
      throw new NotFoundException("Category not found with ID: " + id);
    }
    return ResponseUtils.ok(cat);
  }

  /**
   * Get category by external ID and source GET
   * /api/categories/by-external-id/:externalId?sourceId=&type=
   */
  @GET
  @Path("/by-external-id/{externalId}")
  public Response getCategoryByExternalId(
      @PathParam("externalId") Integer externalId,
      @QueryParam("sourceId") Long sourceId,
      @QueryParam("type") String type) {
    if (sourceId == null) {
      throw new ValidationException("sourceId is required");
    }
    if (type == null || type.isBlank()) {
      throw new ValidationException("type is required");
    }

    var cat = categoryService.findBySourceAndCategoryId(sourceId, externalId, type);
    if (cat == null) {
      throw new NotFoundException(
          "Category not found with external ID: "
              + externalId
              + " and source ID: "
              + sourceId
              + " and type: "
              + type);
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
      cat.setAllowDeny(AllowDenyStatus.fromValue(request.get("allowDeny")));
      categoryService.update(cat);
    }
    return ResponseUtils.ok(cat);
  }

  /** Update blacklist status PATCH /api/categories/:id/blacklist */
  @PATCH
  @Path("/{id}/blacklist")
  public Response updateBlackList(@PathParam("id") Long id, java.util.Map<String, String> request) {
    var cat = categoryService.getById(id);
    if (cat == null) {
      throw new NotFoundException("Category not found with ID: " + id);
    }

    if (request != null && request.get("blackList") != null) {
      cat.setBlackList(
          org.anasoid.iptvorganizer.models.entity.stream.Category.BlackListStatus.fromValue(
              request.get("blackList")));
      categoryService.update(cat);
    }
    return ResponseUtils.ok(cat);
  }
}
