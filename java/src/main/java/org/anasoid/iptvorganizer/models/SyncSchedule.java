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
public class SyncSchedule extends BaseEntity {
    private Long sourceId;
    private String taskType;
    private LocalDateTime nextSync;
    private LocalDateTime lastSync;
    private Integer syncInterval;
}
