package org.anasoid.iptvorganizer.models.filtering;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Defines matching criteria for a filter rule. Supports stream type filtering, category matching,
 * and channel/stream matching.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchCriteria {

  /** Stream types to match: "live", "vod", "series" */
  @JsonProperty("stream_type")
  private List<String> streamType;

  /** Category-specific matching criteria */
  @JsonProperty("categories")
  private CategoryMatch categories;

  /** Channel/stream-specific matching criteria */
  @JsonProperty("channels")
  private ChannelMatch channels;
}
