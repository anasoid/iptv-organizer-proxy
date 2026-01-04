package org.anasoid.iptvorganizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO for SyncLog
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SyncLogDTO {
    private Long id;
    private Long sourceId;
    private String syncType;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String status;
    private Integer itemsAdded;
    private Integer itemsUpdated;
    private Integer itemsDeleted;
    private String errorMessage;
    private Integer durationSeconds;

    /**
     * Convert entity to DTO
     */
    public static SyncLogDTO fromEntity(org.anasoid.iptvorganizer.models.SyncLog entity) {
        if (entity == null) return null;

        return SyncLogDTO.builder()
            .id(entity.getId())
            .sourceId(entity.getSourceId())
            .syncType(entity.getSyncType())
            .startedAt(entity.getStartedAt())
            .completedAt(entity.getCompletedAt())
            .status(entity.getStatus() != null ? entity.getStatus().getValue() : null)
            .itemsAdded(entity.getItemsAdded())
            .itemsUpdated(entity.getItemsUpdated())
            .itemsDeleted(entity.getItemsDeleted())
            .errorMessage(entity.getErrorMessage())
            .durationSeconds(entity.getDurationSeconds())
            .build();
    }
}
