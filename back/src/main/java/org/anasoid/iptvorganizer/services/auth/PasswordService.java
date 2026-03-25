package org.anasoid.iptvorganizer.services.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;

/** Password hashing and verification service using BCrypt */
@ApplicationScoped
public class PasswordService {

  private static final int BCRYPT_COST = 10; // Balance between security and performance

  /** Hash a plain text password using BCrypt */
  public String hashPassword(String plainPassword) {
    if (plainPassword == null || plainPassword.isBlank()) {
      throw new IllegalArgumentException("Password cannot be null or empty");
    }
    return BCrypt.withDefaults().hashToString(BCRYPT_COST, plainPassword.toCharArray());
  }

  /** Verify a plain text password against a BCrypt hash */
  public boolean verifyPassword(String plainPassword, String hashedPassword) {
    if (plainPassword == null || hashedPassword == null) {
      return false;
    }
    BCrypt.Result result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hashedPassword);
    return result.verified;
  }
}
