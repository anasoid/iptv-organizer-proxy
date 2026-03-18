package org.anasoid.iptvorganizer.models.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class BaseEntity {
  protected Long id;
  protected LocalDateTime createdAt;
  protected LocalDateTime updatedAt;
}
