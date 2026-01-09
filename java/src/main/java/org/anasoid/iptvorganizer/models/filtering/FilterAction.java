package org.anasoid.iptvorganizer.models.filtering;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum FilterAction {
  @JsonProperty("include")
  INCLUDE,

  @JsonProperty("exclude")
  EXCLUDE
}
