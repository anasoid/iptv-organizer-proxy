package org.anasoid.iptvorganizer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

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
}
