package org.anasoid.iptvorganizer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a category Maps snake_case field names from frontend to camelCase Java
 * properties
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {
  @JsonProperty("source_id")
  private Long sourceId;

  @JsonProperty("external_id")
  private Integer externalId;

  private String name;

  private String type;

  private Integer num;

  @JsonProperty("allow_deny")
  private String allowDeny;

  @JsonProperty("parent_id")
  private Integer parentId;

  private String labels;
}
