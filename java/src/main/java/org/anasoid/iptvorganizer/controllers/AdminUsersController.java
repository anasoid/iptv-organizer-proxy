package org.anasoid.iptvorganizer.controllers;

import org.anasoid.iptvorganizer.dto.AdminUserDTO;
import org.anasoid.iptvorganizer.dto.request.CreateAdminUserRequest;
import org.anasoid.iptvorganizer.dto.request.UpdateAdminUserRequest;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.AdminUser;
import org.anasoid.iptvorganizer.services.AdminUserService;
import org.anasoid.iptvorganizer.services.auth.PasswordService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDateTime;

/**
 * Admin Users controller
 * CRUD operations for admin users
 */
@Path("/api/admin-users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminUsersController extends BaseController {

    @Inject
    AdminUserService adminUserService;

    @Inject
    PasswordService passwordService;

    /**
     * Get all admin users with pagination
     * GET /api/admin-users?page=1&limit=20
     */
    @GET
    public Uni<?> getAllAdminUsers(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("limit") @DefaultValue("20") int limit) {

        if (page < 1 || limit < 1) {
            return Uni.createFrom().item(
                ApiResponse.error("Page and limit must be greater than 0")
            );
        }

        return Uni.combine().all().unis(
            adminUserService.getAllPaged(page, limit).map(AdminUserDTO::fromEntity).collect().asList(),
            adminUserService.count()
        ).asTuple()
            .map(tuple -> {
                var users = tuple.getItem1();
                long total = tuple.getItem2();
                var pagination = PaginationMeta.of(page, limit, total);
                return ApiResponse.successWithPagination(users, pagination);
            })
            .onFailure().recoverWithItem(ex ->
                ApiResponse.error("Failed to fetch admin users: " + ex.getMessage())
            );
    }

    /**
     * Get admin user by ID
     * GET /api/admin-users/:id
     */
    @GET
    @Path("/{id}")
    public Uni<?> getAdminUser(@PathParam("id") Long id) {
        return adminUserService.getById(id)
            .map(user -> user != null ? ApiResponse.success(AdminUserDTO.fromEntity(user)) : ApiResponse.error("Admin user not found"))
            .onFailure().recoverWithItem(ex ->
                ApiResponse.error("Admin user not found")
            );
    }

    /**
     * Create admin user
     * POST /api/admin-users
     */
    @POST
    public Uni<?> createAdminUser(CreateAdminUserRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return Uni.createFrom().item(
                ApiResponse.error("Username is required")
            );
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return Uni.createFrom().item(
                ApiResponse.error("Password is required")
            );
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return Uni.createFrom().item(
                ApiResponse.error("Email is required")
            );
        }

        // Check if username already exists
        return adminUserService.existsByUsername(request.getUsername())
            .flatMap(exists -> {
                if (exists) {
                    return Uni.createFrom().failure(
                        new ValidationException("Username already exists")
                    );
                }

                String hashedPassword = passwordService.hashPassword(request.getPassword());
                AdminUser user = AdminUser.builder()
                    .username(request.getUsername())
                    .passwordHash(hashedPassword)
                    .email(request.getEmail())
                    .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

                return adminUserService.save(user);
            })
            .map(user -> ApiResponse.success(AdminUserDTO.fromEntity(user)))
            .onFailure().recoverWithItem(ex ->
                ApiResponse.error("Failed to create admin user: " + ex.getMessage())
            );
    }

    /**
     * Update admin user
     * PUT /api/admin-users/:id
     */
    @PUT
    @Path("/{id}")
    public Uni<?> updateAdminUser(@PathParam("id") Long id, UpdateAdminUserRequest request) {
        Long currentUserId = getCurrentUserId();

        return adminUserService.getById(id)
            .flatMap(user -> {
                if (request.getEmail() != null && !request.getEmail().isBlank()) {
                    user.setEmail(request.getEmail());
                }

                if (request.getIsActive() != null) {
                    user.setIsActive(request.getIsActive());
                }

                if (request.getPassword() != null && !request.getPassword().isBlank()) {
                    String hashedPassword = passwordService.hashPassword(request.getPassword());
                    user.setPasswordHash(hashedPassword);
                }

                user.setUpdatedAt(LocalDateTime.now());
                return adminUserService.update(user)
                    .map(v -> user);  // Return the updated user after successful update
            })
            .map(user -> ApiResponse.success(AdminUserDTO.fromEntity(user)))
            .onFailure().recoverWithItem(ex ->
                ApiResponse.error("Failed to update admin user: " + ex.getMessage())
            );
    }

    /**
     * Delete admin user
     * DELETE /api/admin-users/:id
     */
    @DELETE
    @Path("/{id}")
    public Uni<?> deleteAdminUser(@PathParam("id") Long id) {
        Long currentUserId = getCurrentUserId();

        // Prevent self-deletion
        if (id.equals(currentUserId)) {
            return Uni.createFrom().item(
                ApiResponse.error("Cannot delete your own user account")
            );
        }

        return adminUserService.delete(id)
            .map(v -> ApiResponse.success("Admin user deleted successfully"))
            .onFailure().recoverWithItem(ex ->
                ApiResponse.error("Failed to delete admin user: " + ex.getMessage())
            );
    }
}
