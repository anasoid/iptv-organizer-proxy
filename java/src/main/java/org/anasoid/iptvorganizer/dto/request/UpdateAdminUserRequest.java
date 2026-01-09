package org.anasoid.iptvorganizer.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request DTO for updating admin user */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAdminUserRequest {
  private String email;
  private Boolean isActive;
  private String password; // Optional, only set if changing password
}
