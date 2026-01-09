package org.anasoid.iptvorganizer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating sync log entries Maps snake_case field names from frontend to camelCase
 * Java properties
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSyncLogRequest {
  @JsonProperty("source_id")
  private Long sourceId;

  @JsonProperty("sync_type")
  private String syncType;

  @JsonProperty("started_at")
  private LocalDateTime startedAt;

  @JsonProperty("completed_at")
  private LocalDateTime completedAt;

  private String status;

  @JsonProperty("items_added")
  private Integer itemsAdded;

  @JsonProperty("items_updated")
  private Integer itemsUpdated;

  @JsonProperty("items_deleted")
  private Integer itemsDeleted;

  @JsonProperty("error_message")
  private String errorMessage;

  @JsonProperty("duration_seconds")
  private Integer durationSeconds;
}
