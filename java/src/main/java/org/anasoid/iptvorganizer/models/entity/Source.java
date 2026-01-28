package org.anasoid.iptvorganizer.models.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Source extends BaseEntity {
  private String name;
  private String url;
  private String username;

  @JsonIgnore private String password;

  private Integer syncInterval;
  private LocalDateTime lastSync;
  private LocalDateTime nextSync;
  private Boolean isActive;
  private Boolean enableProxy;
  private Boolean disableStreamProxy;
  private Boolean streamFollowLocation;
  private Boolean useRedirect;
  private Boolean useRedirectXmltv;
}
