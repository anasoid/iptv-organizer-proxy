package org.anasoid.iptvorganizer.controllers;

import org.anasoid.iptvorganizer.dto.CategoryDTO;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.models.Category;
import org.anasoid.iptvorganizer.services.CategoryService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * Categories controller
 */
@Path("/api/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class CategoriesController extends BaseController {

    @Inject
    CategoryService categoryService;

    /**
     * Get categories with filters
     * GET /api/categories?source_id=&page=1&limit=20&search=&category_type=
     */
    @GET
    public Uni<?> getCategories(
            @QueryParam("source_id") Long sourceId,
            @QueryParam("category_type") String categoryType,
            @QueryParam("search") String search,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("limit") @DefaultValue("20") int limit) {

        if (sourceId == null) {
            return Uni.createFrom().item(ApiResponse.error("source_id is required"));
        }

        return Uni.combine().all().unis(
            categoryService.findBySourceIdFiltered(sourceId, categoryType, search, page, limit).map(CategoryDTO::fromEntity).collect().asList(),
            categoryService.countBySourceIdFiltered(sourceId, categoryType, search)
        ).asTuple()
            .map(tuple -> ApiResponse.successWithPagination(tuple.getItem1(), PaginationMeta.of(page, limit, tuple.getItem2())))
            .onFailure().recoverWithItem(ex -> ApiResponse.error("Failed to fetch categories: " + ex.getMessage()));
    }

    /**
     * Get category by ID
     * GET /api/categories/:id?source_id=
     */
    @GET
    @Path("/{id}")
    public Uni<?> getCategory(@PathParam("id") Long id) {
        return categoryService.getById(id)
            .map(cat -> cat != null ? ApiResponse.success(CategoryDTO.fromEntity(cat)) : ApiResponse.error("Category not found"))
            .onFailure().recoverWithItem(ex -> ApiResponse.error("Category not found"));
    }

    /**
     * Update allow-deny status
     * PATCH /api/categories/:id/allow-deny
     */
    @PATCH
    @Path("/{id}/allow-deny")
    public Uni<?> updateAllowDeny(@PathParam("id") Long id, java.util.Map<String, String> request) {
        return categoryService.getById(id)
            .flatMap(cat -> {
                if (request != null && request.get("allowDeny") != null) {
                    cat.setAllowDeny(request.get("allowDeny"));
                    return categoryService.update(cat).map(v -> cat);
                }
                return Uni.createFrom().item(cat);
            })
            .map(cat -> ApiResponse.success(CategoryDTO.fromEntity(cat)))
            .onFailure().recoverWithItem(ex -> ApiResponse.error("Failed to update category: " + ex.getMessage()));
    }
}
