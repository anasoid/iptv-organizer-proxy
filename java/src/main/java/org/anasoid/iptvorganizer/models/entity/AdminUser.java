package org.anasoid.iptvorganizer.models.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class AdminUser extends BaseEntity {
  private String username;
  private String passwordHash;
  private String email;
  private Boolean isActive;
  private LocalDateTime lastLogin;
}
