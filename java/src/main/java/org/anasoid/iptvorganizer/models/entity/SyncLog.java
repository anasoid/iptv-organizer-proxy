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

  public enum SyncLogStatus {
    RUNNING("running"),
    COMPLETED("completed"),
    FAILED("failed"),
    INTERRUPTED("interrupted");

    private final String value;

    SyncLogStatus(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    /** Convert string value to enum */
    public static SyncLogStatus fromValue(String value) {
      if (value == null) {
        return null;
      }
      for (SyncLogStatus status : SyncLogStatus.values()) {
        if (status.value.equalsIgnoreCase(value)) {
          return status;
        }
      }
      throw new IllegalArgumentException("Unknown sync log status: " + value);
    }
  }
}
