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
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.entity.AdminUser;
import org.anasoid.iptvorganizer.services.AdminUserService;
import org.anasoid.iptvorganizer.services.auth.PasswordService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

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
      return ResponseUtils.badRequest("Page and limit must be greater than 0");
    }

    try {
      var users =
          adminUserService.getAllPaged(page, limit).stream()
              .map(AdminUserDTO::fromEntity)
              .collect(Collectors.toList());
      long total = adminUserService.count();
      var pagination = PaginationMeta.of(page, limit, total);
      return ResponseUtils.okWithPagination(users, pagination);
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to fetch admin users: " + ex.getMessage());
    }
  }

  /** Get admin user by ID GET /api/admin-users/:id */
  @GET
  @Path("/{id}")
  public Response getAdminUser(@PathParam("id") Long id) {
    try {
      AdminUser user = adminUserService.getById(id);
      if (user != null) {
        return ResponseUtils.ok(AdminUserDTO.fromEntity(user));
      } else {
        return ResponseUtils.notFound("Admin user not found");
      }
    } catch (Exception ex) {
      return ResponseUtils.notFound("Admin user not found");
    }
  }

  /** Create admin user POST /api/admin-users */
  @POST
  public Response createAdminUser(CreateAdminUserRequest request) {
    if (request.getUsername() == null || request.getUsername().isBlank()) {
      return ResponseUtils.badRequest("Username is required");
    }
    if (request.getPassword() == null || request.getPassword().isBlank()) {
      return ResponseUtils.badRequest("Password is required");
    }
    if (request.getEmail() == null || request.getEmail().isBlank()) {
      return ResponseUtils.badRequest("Email is required");
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
      return ResponseUtils.created(AdminUserDTO.fromEntity(savedUser));
    } catch (ValidationException ex) {
      return ResponseUtils.badRequest(ex.getMessage());
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to create admin user: " + ex.getMessage());
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
        return ResponseUtils.notFound("Admin user not found");
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
      return ResponseUtils.ok(AdminUserDTO.fromEntity(user));
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to update admin user: " + ex.getMessage());
    }
  }

  /** Delete admin user DELETE /api/admin-users/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteAdminUser(@PathParam("id") Long id) {
    Long currentUserId = getCurrentUserId();

    // Prevent self-deletion
    if (id.equals(currentUserId)) {
      return ResponseUtils.badRequest("Cannot delete your own user account");
    }

    try {
      adminUserService.delete(id);
      return ResponseUtils.okMessage("Admin user deleted successfully");
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to delete admin user: " + ex.getMessage());
    }
  }
}
