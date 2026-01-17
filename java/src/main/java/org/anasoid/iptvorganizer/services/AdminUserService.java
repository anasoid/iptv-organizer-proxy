package org.anasoid.iptvorganizer.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.AdminUser;
import org.anasoid.iptvorganizer.repositories.AdminUserRepository;

@ApplicationScoped
public class AdminUserService extends BaseService<AdminUser, AdminUserRepository> {

  @Inject AdminUserRepository repository;

  @Override
  protected AdminUserRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(AdminUser user) {
    if (user.getUsername() == null || user.getUsername().isBlank()) {
      throw new IllegalArgumentException("Username is required");
    }
    if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
      throw new IllegalArgumentException("Password hash is required");
    }
    if (user.getIsActive() == null) {
      user.setIsActive(true);
    }
    return repository.insert(user);
  }

  /** Check if username exists */
  public Boolean existsByUsername(String username) {
    return repository.usernameExists(username);
  }
}
