package org.anasoid.iptvorganizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
  private Long sourceId;
  private Long filterId;
  private String username;
  private String name;
  private String email;
  private LocalDate expiryDate;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean isActive;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean hideAdultContent;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean useRedirect;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean useRedirectXmltv;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean enableProxy;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean disableStreamProxy;

  @JsonSerialize(using = BooleanAsIntSerializer.class)
  private Boolean streamFollowLocation;

  private LocalDateTime lastLogin;
  private LocalDateTime createdAt;
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
