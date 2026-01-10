package org.anasoid.iptvorganizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.anasoid.iptvorganizer.config.BooleanAsIntSerializer;
import org.anasoid.iptvorganizer.models.Source;

/** DTO for Source - excludes password from API responses All boolean fields serialize as 0/1 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceDTO {
  private Long id;
  private String name;
  private String url;
  private String username;
  private Integer syncInterval;
  private LocalDateTime lastSync;
  private LocalDateTime nextSync;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean isActive;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean enableProxy;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean disableStreamProxy;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean streamFollowLocation;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** Convert entity to DTO (excludes password) */
  public static SourceDTO fromEntity(Source entity) {
    if (entity == null) return null;

    return SourceDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .url(entity.getUrl())
        .username(entity.getUsername())
        .syncInterval(entity.getSyncInterval())
        .lastSync(entity.getLastSync())
        .nextSync(entity.getNextSync())
        .isActive(entity.getIsActive())
        .enableProxy(entity.getEnableProxy())
        .disableStreamProxy(entity.getDisableStreamProxy())
        .streamFollowLocation(entity.getStreamFollowLocation())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
