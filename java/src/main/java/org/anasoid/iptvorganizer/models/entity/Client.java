package org.anasoid.iptvorganizer.models.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;
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
  private String notes;
  private LocalDateTime lastLogin;
}
