package org.anasoid.iptvorganizer.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating admin user
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdminUserRequest {
    private String username;
    private String password;
    private String email;
    private Boolean isActive;
}
