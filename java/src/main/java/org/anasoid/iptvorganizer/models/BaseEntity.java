package org.anasoid.iptvorganizer.models;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseEntity {
  protected Long id;
  protected LocalDateTime createdAt;
  protected LocalDateTime updatedAt;
}
