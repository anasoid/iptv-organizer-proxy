package org.anasoid.iptvorganizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.anasoid.iptvorganizer.models.stream.Category;

/** DTO for Category */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryDTO {
  private Long id;

  @JsonProperty("source_id")
  private Long sourceId;

  @JsonProperty("category_id")
  private Integer externalId;

  @JsonProperty("category_name")
  private String name;

  @JsonProperty("category_type")
  private String type;

  private Integer num;
  private String allowDeny;
  private Integer parentId;
  private String labels;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** Convert entity to DTO */
  public static CategoryDTO fromEntity(Category entity) {
    if (entity == null) return null;

    return CategoryDTO.builder()
        .id(entity.getId())
        .sourceId(entity.getSourceId())
        .externalId(entity.getExternalId())
        .name(entity.getName())
        .type(entity.getType())
        .num(entity.getNum())
        .allowDeny(entity.getAllowDeny())
        .parentId(entity.getParentId())
        .labels(entity.getLabels())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
