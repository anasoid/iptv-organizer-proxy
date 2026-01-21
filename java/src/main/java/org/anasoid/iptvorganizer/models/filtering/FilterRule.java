package org.anasoid.iptvorganizer.models.filtering;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single filter rule with include/exclude logic and matching criteria. Supports both
 * legacy field-based rules and new matching criteria-based rules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterRule {
  // Legacy fields (for backward compatibility with existing filters)
  private FilterField field;
  private String pattern;
  private Object value;

  // New fields (PHP-compatible structure)
  private String name;

  @JsonProperty("type")
  private FilterAction type;

  @JsonProperty("match")
  private MatchCriteria match;

  // Legacy field (for backward compatibility)
  private FilterAction action;
}
