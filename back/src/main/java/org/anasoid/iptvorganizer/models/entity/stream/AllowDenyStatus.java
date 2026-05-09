package org.anasoid.iptvorganizer.models.entity.stream;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AllowDenyStatus {
  ALLOW("allow"),
  DENY("deny");

  private final String value;

  AllowDenyStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  /** Convert string value to enum */
  public static AllowDenyStatus fromValue(String value) {
    if (value == null) {
      return null;
    }
    for (AllowDenyStatus status : AllowDenyStatus.values()) {
      if (status.value.equalsIgnoreCase(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown allow/deny status: " + value);
  }
}
