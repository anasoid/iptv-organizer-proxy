package org.anasoid.iptvorganizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.anasoid.iptvorganizer.config.BooleanAsIntSerializer;
import org.anasoid.iptvorganizer.models.entity.Client;

/** DTO for Client - excludes password Boolean fields serialize as 0/1 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientDTO {
  private Long id;

  @JsonProperty("source_id")
  private Long sourceId;

  @JsonProperty("filter_id")
  private Long filterId;

  private String username;
  private String name;
  private String email;

  @JsonProperty("expiry_date")
  private LocalDate expiryDate;

  @JsonProperty("is_active")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean isActive;

  @JsonProperty("hide_adult_content")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean hideAdultContent;

  @JsonProperty("use_redirect")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean useRedirect;

  @JsonProperty("use_redirect_xmltv")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean useRedirectXmltv;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean enableProxy;

  @JsonProperty("disablestreamproxy")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean disableStreamProxy;

  @JsonProperty("stream_follow_location")
  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean streamFollowLocation;

  @JsonProperty("last_login")
  private LocalDateTime lastLogin;

  @JsonProperty("created_at")
  private LocalDateTime createdAt;

  @JsonProperty("updated_at")
  private LocalDateTime updatedAt;

  /** Convert entity to DTO (excludes password) */
  public static ClientDTO fromEntity(Client entity) {
    if (entity == null) return null;

    return ClientDTO.builder()
        .id(entity.getId())
        .sourceId(entity.getSourceId())
        .filterId(entity.getFilterId())
        .username(entity.getUsername())
        .name(entity.getName())
        .email(entity.getEmail())
        .expiryDate(entity.getExpiryDate())
        .isActive(entity.getIsActive())
        .hideAdultContent(entity.getHideAdultContent())
        .useRedirect(entity.getUseRedirect())
        .useRedirectXmltv(entity.getUseRedirectXmltv())
        .enableProxy(entity.getEnableProxy())
        .disableStreamProxy(entity.getDisableStreamProxy())
        .streamFollowLocation(entity.getStreamFollowLocation())
        .lastLogin(entity.getLastLogin())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
