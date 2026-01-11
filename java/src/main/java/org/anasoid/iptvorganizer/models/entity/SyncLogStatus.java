package org.anasoid.iptvorganizer.models.entity;

/** Enumeration for sync log status values */
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
