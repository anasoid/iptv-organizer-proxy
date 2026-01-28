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
import org.anasoid.iptvorganizer.models.entity.Source;

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

  @JsonProperty("sync_interval")
  private Integer syncInterval;

  @JsonProperty("last_sync")
  private LocalDateTime lastSync;

  @JsonProperty("next_sync")
  private LocalDateTime nextSync;

  @JsonProperty("is_active")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean isActive;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean enableProxy;

  @JsonProperty("disablestreamproxy")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean disableStreamProxy;

  @JsonProperty("stream_follow_location")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean streamFollowLocation;

  @JsonProperty("use_redirect")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean useRedirect;

  @JsonProperty("use_redirect_xmltv")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean useRedirectXmltv;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  @JsonProperty("updated_at")
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
        .useRedirect(entity.getUseRedirect())
        .useRedirectXmltv(entity.getUseRedirectXmltv())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
