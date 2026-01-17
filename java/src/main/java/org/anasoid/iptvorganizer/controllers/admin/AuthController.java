package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import org.anasoid.iptvorganizer.dto.AdminUserDTO;
import org.anasoid.iptvorganizer.dto.request.LoginRequest;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.models.entity.AdminUser;
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
  public Response login(LoginRequest request) {
    try {
      if (request.getUsername() == null
          || request.getUsername().isBlank()
          || request.getPassword() == null
          || request.getPassword().isBlank()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(ApiResponse.error("Username and password are required"))
            .build();
      }

      Map<String, Object> loginResponse =
          authService.login(request.getUsername(), request.getPassword());

      // Extract token and user from login response
      String token = (String) loginResponse.get("token");
      AdminUser user = (AdminUser) loginResponse.get("user");

      // Create response with token and user DTO
      Map<String, Object> responseData = new HashMap<>();
      responseData.put("token", token);
      responseData.put("user", AdminUserDTO.fromEntity(user));

      return Response.ok(responseData).build();
    } catch (Exception ex) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(ApiResponse.error(ex.getMessage()))
          .build();
    }
  }

  /** Get current user info - PROTECTED GET /api/auth/me */
  @GET
  @Path("/me")
  @RolesAllowed("admin")
  public Response getCurrentUser() {
    try {
      Long userId = getCurrentUserId();
      AdminUser user = adminUserService.getById(userId);
      return Response.ok(ApiResponse.success(AdminUserDTO.fromEntity(user))).build();
    } catch (Exception ex) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ApiResponse.error("Failed to get user info: " + ex.getMessage()))
          .build();
    }
  }

  /** Logout endpoint - PROTECTED POST /api/auth/logout */
  @POST
  @Path("/logout")
  @RolesAllowed("admin")
  public Response logout() {
    // JWT is stateless, logout is just a client-side action
    return Response.ok(ApiResponse.success("Logged out successfully")).build();
  }
}
