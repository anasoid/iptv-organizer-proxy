package org.anasoid.iptvorganizer.controllers.admin;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.anasoid.iptvorganizer.dto.AdminUserDTO;
import org.anasoid.iptvorganizer.dto.request.LoginRequest;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.services.AdminUserService;
import org.anasoid.iptvorganizer.services.auth.AuthService;

/**
 * Authentication controller POST /api/auth/login - Public endpoint for login GET /api/auth/me -
 * Protected endpoint for current user info POST /api/auth/logout - Protected endpoint for logout
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthController extends BaseController {

  @Inject AuthService authService;

  @Inject AdminUserService adminUserService;

  /** Login endpoint - PUBLIC (no @RolesAllowed) POST /api/auth/login */
  @POST
  @Path("/login")
  public Uni<?> login(LoginRequest request) {
    if (request.getUsername() == null
        || request.getUsername().isBlank()
        || request.getPassword() == null
        || request.getPassword().isBlank()) {
      return Uni.createFrom().item(ApiResponse.error("Username and password are required"));
    }

    return authService
        .login(request.getUsername(), request.getPassword())
        .map(
            loginResponse -> {
              // Extract token and user from login response
              String token = (String) loginResponse.get("token");
              org.anasoid.iptvorganizer.models.entity.AdminUser user =
                  (org.anasoid.iptvorganizer.models.entity.AdminUser) loginResponse.get("user");

              // Create response with token and user DTO
              var responseData = new java.util.HashMap<String, Object>();
              responseData.put("token", token);
              responseData.put("user", AdminUserDTO.fromEntity(user));
              return (Object) responseData;
            })
        .onFailure()
        .recoverWithItem(ex -> (Object) ApiResponse.error(ex.getMessage()));
  }

  /** Get current user info - PROTECTED GET /api/auth/me */
  @GET
  @Path("/me")
  @RolesAllowed("admin")
  public Uni<?> getCurrentUser() {
    Long userId = getCurrentUserId();
    return adminUserService
        .getById(userId)
        .map(user -> ApiResponse.success(AdminUserDTO.fromEntity(user)))
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Failed to get user info: " + ex.getMessage()));
  }

  /** Logout endpoint - PROTECTED POST /api/auth/logout */
  @POST
  @Path("/logout")
  @RolesAllowed("admin")
  public Uni<?> logout() {
    // JWT is stateless, logout is just a client-side action
    // But we can return success response
    return Uni.createFrom().item(ApiResponse.success("Logged out successfully"));
  }
}
