package org.anasoid.iptvorganizer.models.entity;

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
public class SyncSchedule extends BaseEntity {
  private Long sourceId;
  private String taskType;
  private LocalDateTime nextSync;
  private LocalDateTime lastSync;
  private Integer syncInterval;
}
