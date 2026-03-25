package org.anasoid.iptvorganizer.models.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.anasoid.iptvorganizer.models.enums.ClientConnectXmltvMode;
import org.anasoid.iptvorganizer.models.enums.ClientConnectXtreamApiMode;
import org.anasoid.iptvorganizer.models.enums.ClientConnectXtreamStreamMode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Client extends BaseEntity {
  private Long sourceId;
  private Long filterId;
  private String username;
  private String password;
  private String name;
  private String email;
  private LocalDate expiryDate;
  private Boolean isActive;
  private Boolean hideAdultContent;
  private Boolean enableProxy; // NOT NULL, default false
  private String notes;
  private LocalDateTime lastLogin;
  private ClientConnectXtreamApiMode connectXtreamApi;
  private ClientConnectXtreamStreamMode connectXtreamStream;
  private ClientConnectXmltvMode connectXmltv;
}
