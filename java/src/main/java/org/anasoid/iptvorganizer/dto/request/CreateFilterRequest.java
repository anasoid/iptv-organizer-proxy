package org.anasoid.iptvorganizer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a filter Maps snake_case field names from frontend to camelCase Java
 * properties
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFilterRequest {
  private String name;
  private String description;

  @JsonProperty("filter_config")
  private String filterConfig;

  @JsonProperty("use_source_filter")
  private Boolean useSourceFilter;

  private String favoris;
}
