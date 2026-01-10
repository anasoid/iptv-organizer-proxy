package org.anasoid.iptvorganizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.anasoid.iptvorganizer.config.BooleanAsIntSerializer;
import org.anasoid.iptvorganizer.models.Filter;
import org.anasoid.iptvorganizer.models.stream.*;

/** DTO for Filter Boolean fields serialize as 0/1 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilterDTO {
  private Long id;
  private String name;
  private String description;
  private String filterConfig;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean useSourceFilter;

  private String favoris;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** Convert entity to DTO */
  public static FilterDTO fromEntity(Filter entity) {
    if (entity == null) return null;

    return FilterDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .description(entity.getDescription())
        .filterConfig(entity.getFilterConfig())
        .useSourceFilter(entity.getUseSourceFilter())
        .favoris(entity.getFavoris())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
