package org.anasoid.iptvorganizer.models;

import java.time.LocalDate;
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
  private Integer maxConnections;
  private String notes;
  private LocalDateTime lastLogin;
}
