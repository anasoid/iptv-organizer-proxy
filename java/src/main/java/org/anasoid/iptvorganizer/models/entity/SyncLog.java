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
public class SyncLog extends BaseEntity {
  private Long sourceId;
  private String syncType;
  private LocalDateTime startedAt;
  private LocalDateTime completedAt;
  private SyncLogStatus status;
  private Integer itemsAdded;
  private Integer itemsUpdated;
  private Integer itemsDeleted;
  private String errorMessage;
  private Integer durationSeconds;

  // Note: createdAt and updatedAt are not persisted in sync_logs table
  // startedAt and completedAt serve as creation/update time markers
}
