package org.anasoid.iptvorganizer.models.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

  @JsonIgnore private String passwordHash;

  private String email;
  private Boolean isActive;
  private LocalDateTime lastLogin;
}
