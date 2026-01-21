package org.anasoid.iptvorganizer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a client Maps snake_case field names from frontend to camelCase Java
 * properties
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClientRequest {
  @JsonProperty("source_id")
  private Long sourceId;

  @JsonProperty("filter_id")
  private Long filterId;

  private String username;
  private String password;
  private String name;
  private String email;

  @JsonProperty("expiry_date")
  private LocalDate expiryDate;

  @JsonProperty("is_active")
  private Boolean isActive;

  @JsonProperty("hide_adult_content")
  private Boolean hideAdultContent;

  private String notes;
}
