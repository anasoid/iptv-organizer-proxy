package org.anasoid.iptvorganizer.dto.xtream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Xtream Codes API category object */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class XtreamCategory {
  @JsonProperty("category_id")
  private String categoryId;

  @JsonProperty("category_name")
  private String categoryName;

  @JsonProperty("parent_id")
  private Integer parentId;
}
