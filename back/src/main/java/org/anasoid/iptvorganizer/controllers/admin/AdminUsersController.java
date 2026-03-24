package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.entity.AdminUser;
import org.anasoid.iptvorganizer.services.AdminUserService;
import org.anasoid.iptvorganizer.services.auth.PasswordService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Admin Users controller CRUD operations for admin users */
@Path("/api/admin-users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminUsersController extends BaseController {

  @Inject AdminUserService adminUserService;

  @Inject PasswordService passwordService;

  /** Get all admin users with pagination GET /api/admin-users?page=1&limit=20 */
  @GET
  public Response getAllAdminUsers(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit) {

    if (page < 1 || limit < 1) {
      throw new ValidationException("Page and limit must be greater than 0");
    }

    var users = adminUserService.getAllPaged(page, limit);
    long total = adminUserService.count();
    var pagination = PaginationMeta.of(page, limit, total);
    return ResponseUtils.okWithPagination(users, pagination);
  }

  /** Get admin user by ID GET /api/admin-users/:id */
  @GET
  @Path("/{id}")
  public Response getAdminUser(@PathParam("id") Long id) {
    AdminUser user = adminUserService.getById(id);
    if (user == null) {
      throw new NotFoundException("Admin user not found with ID: " + id);
    }
    return ResponseUtils.ok(user);
  }

  /** Create admin user POST /api/admin-users */
  @POST
  public Response createAdminUser(AdminUser request) {
    if (request.getUsername() == null || request.getUsername().isBlank()) {
      throw new ValidationException("Username is required");
    }
    if (request.getPasswordHash() == null || request.getPasswordHash().isBlank()) {
      throw new ValidationException("Password is required");
    }
    if (request.getEmail() == null || request.getEmail().isBlank()) {
      throw new ValidationException("Email is required");
    }

    // Check if username already exists
    if (adminUserService.existsByUsername(request.getUsername())) {
      throw new ValidationException("Username already exists");
    }

    // Hash the password (plain password is passed as passwordHash initially)
    String hashedPassword = passwordService.hashPassword(request.getPasswordHash());
    request.setPasswordHash(hashedPassword);

    // Set defaults
    if (request.getIsActive() == null) {
      request.setIsActive(true);
    }

    AdminUser savedUser = adminUserService.save(request);
    return ResponseUtils.created(savedUser);
  }

  /** Update admin user PUT /api/admin-users/:id */
  @PUT
  @Path("/{id}")
  public Response updateAdminUser(@PathParam("id") Long id, AdminUser request) {
    Long currentUserId = getCurrentUserId();

    AdminUser user = adminUserService.getById(id);
    if (user == null) {
      throw new NotFoundException("Admin user not found with ID: " + id);
    }

    // Merge non-null fields from request
    if (request.getEmail() != null && !request.getEmail().isBlank()) {
      user.setEmail(request.getEmail());
    }

    if (request.getIsActive() != null) {
      user.setIsActive(request.getIsActive());
    }

    // If passwordHash is provided (plain password from request), hash it
    if (request.getPasswordHash() != null && !request.getPasswordHash().isBlank()) {
      String hashedPassword = passwordService.hashPassword(request.getPasswordHash());
      user.setPasswordHash(hashedPassword);
    }

    adminUserService.update(user);
    return ResponseUtils.ok(user);
  }

  /** Delete admin user DELETE /api/admin-users/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteAdminUser(@PathParam("id") Long id) {
    Long currentUserId = getCurrentUserId();

    // Prevent self-deletion
    if (id.equals(currentUserId)) {
      throw new ValidationException("Cannot delete your own user account");
    }

    adminUserService.delete(id);
    return ResponseUtils.okMessage("Admin user deleted successfully");
  }
}
