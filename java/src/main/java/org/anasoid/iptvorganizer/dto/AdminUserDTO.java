package org.anasoid.iptvorganizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.anasoid.iptvorganizer.config.BooleanAsIntSerializer;
import org.anasoid.iptvorganizer.models.AdminUser;
import org.anasoid.iptvorganizer.models.stream.*;

/**
 * DTO for AdminUser - excludes password hash from API responses All boolean fields serialize as 0/1
 * for frontend compatibility Fields use snake_case naming for frontend compatibility
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUserDTO {
  private Long id;
  private String username;
  private String email;

  @JsonProperty("is_active")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean isActive;

  @JsonProperty("last_login")
  private LocalDateTime lastLogin;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  @JsonProperty("updated_at")
  private LocalDateTime updatedAt;

  /** Convert entity to DTO (excludes password hash) */
  public static AdminUserDTO fromEntity(AdminUser entity) {
    if (entity == null) return null;

    return AdminUserDTO.builder()
        .id(entity.getId())
        .username(entity.getUsername())
        .email(entity.getEmail())
        .isActive(entity.getIsActive())
        .lastLogin(entity.getLastLogin())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
