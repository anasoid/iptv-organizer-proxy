package org.anasoid.iptvorganizer.models.filtering;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Category-specific matching criteria. Supports matching by name patterns and labels.
 *
 * <p>Matching logic:
 *
 * <ul>
 *   <li>by_name: ANY pattern matches (OR logic)
 *   <li>by_labels: ANY pattern matches ANY label (OR logic)
 *   <li>Both name and labels: BOTH must match (AND logic)
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryMatch {

  /** Category name patterns with wildcard support (* and ?) */
  @JsonProperty("by_name")
  private List<String> byName;

  /** Category label patterns to match against category labels */
  @JsonProperty("by_labels")
  private List<String> byLabels;
}
