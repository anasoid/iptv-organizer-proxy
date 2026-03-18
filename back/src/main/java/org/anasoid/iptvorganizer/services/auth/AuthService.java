package org.anasoid.iptvorganizer.services.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.AdminUser;
import org.anasoid.iptvorganizer.repositories.AdminUserRepository;

/** Authentication service for admin user login */
@ApplicationScoped
public class AuthService {

  @Inject AdminUserRepository adminUserRepository;

  @Inject PasswordService passwordService;

  @Inject JwtService jwtService;

  /**
   * Authenticate user and return JWT token with user info
   *
   * @param username Admin username
   * @param password Plain text password
   * @return Map with token and user object
   */
  public Map<String, Object> login(String username, String password) {
    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      throw new SecurityException("Username and password are required");
    }

    // Find user by username
    AdminUser user = adminUserRepository.findByUsername(username);
    if (user == null) {
      throw new SecurityException("Invalid username or password");
    }

    // Check if user is active
    if (!Boolean.TRUE.equals(user.getIsActive())) {
      throw new SecurityException("Account is inactive");
    }

    // Verify password
    if (!passwordService.verifyPassword(password, user.getPasswordHash())) {
      throw new SecurityException("Invalid username or password");
    }

    // Update last login timestamp
    user.setLastLogin(LocalDateTime.now());
    adminUserRepository.updateLastLogin(user.getId(), user.getLastLogin());

    // Generate JWT token
    String token = jwtService.generateToken(user);
    Map<String, Object> response = new HashMap<>();
    response.put("token", token);
    response.put("user", user);
    return response;
  }

  /** Get current user info from database */
  public AdminUser getCurrentUser(Long userId) {
    if (userId == null) {
      throw new IllegalArgumentException("User ID is required");
    }
    return adminUserRepository.findById(userId);
  }
}
