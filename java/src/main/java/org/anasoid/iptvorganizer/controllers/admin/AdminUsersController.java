package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.dto.AdminUserDTO;
import org.anasoid.iptvorganizer.dto.request.CreateAdminUserRequest;
import org.anasoid.iptvorganizer.dto.request.UpdateAdminUserRequest;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.entity.AdminUser;
import org.anasoid.iptvorganizer.services.AdminUserService;
import org.anasoid.iptvorganizer.services.auth.PasswordService;

/** Admin Users controller CRUD operations for admin users */
@Path("/api/admin-users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminUsersController extends BaseController {

  @Inject AdminUserService adminUserService;

  @Inject PasswordService passwordService;

  /** Get all admin users with pagination GET /api/admin-users?page=1&limit=20 */
  @GET
  public Response getAllAdminUsers(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (page < 1 || limit < 1) {
      return Response.ok(ApiResponse.error("Page and limit must be greater than 0")).build();
    }

    try {
      var users =
          adminUserService.getAllPaged(page, limit).stream()
              .map(AdminUserDTO::fromEntity)
              .collect(Collectors.toList());
      long total = adminUserService.count();
      var pagination = PaginationMeta.of(page, limit, total);
      return Response.ok(ApiResponse.successWithPagination(users, pagination)).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to fetch admin users: " + ex.getMessage()))
          .build();
    }
  }

  /** Get admin user by ID GET /api/admin-users/:id */
  @GET
  @Path("/{id}")
  public Response getAdminUser(@PathParam("id") Long id) {
    try {
      AdminUser user = adminUserService.getById(id);
      if (user != null) {
        return Response.ok(ApiResponse.success(AdminUserDTO.fromEntity(user))).build();
      } else {
        return Response.ok(ApiResponse.error("Admin user not found")).build();
      }
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Admin user not found")).build();
    }
  }

  /** Create admin user POST /api/admin-users */
  @POST
  public Response createAdminUser(CreateAdminUserRequest request) {
    if (request.getUsername() == null || request.getUsername().isBlank()) {
      return Response.ok(ApiResponse.error("Username is required")).build();
    }
    if (request.getPassword() == null || request.getPassword().isBlank()) {
      return Response.ok(ApiResponse.error("Password is required")).build();
    }
    if (request.getEmail() == null || request.getEmail().isBlank()) {
      return Response.ok(ApiResponse.error("Email is required")).build();
    }

    try {
      // Check if username already exists
      if (adminUserService.existsByUsername(request.getUsername())) {
        throw new ValidationException("Username already exists");
      }

      String hashedPassword = passwordService.hashPassword(request.getPassword());
      AdminUser user =
          AdminUser.builder()
              .username(request.getUsername())
              .passwordHash(hashedPassword)
              .email(request.getEmail())
              .isActive(request.getIsActive() != null ? request.getIsActive() : true)
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();

      AdminUser savedUser = adminUserService.save(user);
      return Response.ok(ApiResponse.success(AdminUserDTO.fromEntity(savedUser))).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to create admin user: " + ex.getMessage()))
          .build();
    }
  }

  /** Update admin user PUT /api/admin-users/:id */
  @PUT
  @Path("/{id}")
  public Response updateAdminUser(@PathParam("id") Long id, UpdateAdminUserRequest request) {
    Long currentUserId = getCurrentUserId();

    try {
      AdminUser user = adminUserService.getById(id);
      if (user == null) {
        return Response.ok(ApiResponse.error("Admin user not found")).build();
      }

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
      adminUserService.update(user);
      return Response.ok(ApiResponse.success(AdminUserDTO.fromEntity(user))).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to update admin user: " + ex.getMessage()))
          .build();
    }
  }

  /** Delete admin user DELETE /api/admin-users/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteAdminUser(@PathParam("id") Long id) {
    Long currentUserId = getCurrentUserId();

    // Prevent self-deletion
    if (id.equals(currentUserId)) {
      return Response.ok(ApiResponse.error("Cannot delete your own user account")).build();
    }

    try {
      adminUserService.delete(id);
      return Response.ok(ApiResponse.success("Admin user deleted successfully")).build();
    } catch (Exception ex) {
      return Response.ok(ApiResponse.error("Failed to delete admin user: " + ex.getMessage()))
          .build();
    }
  }
}
