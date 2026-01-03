package org.anasoid.iptvorganizer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a category
 * Maps snake_case field names from frontend to camelCase Java properties
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {
    @JsonProperty("source_id")
    private Long sourceId;

    @JsonProperty("category_id")
    private Integer categoryId;

    @JsonProperty("category_name")
    private String categoryName;

    @JsonProperty("category_type")
    private String categoryType;

    private Integer num;

    @JsonProperty("allow_deny")
    private String allowDeny;

    @JsonProperty("parent_id")
    private Integer parentId;

    private String labels;
}
