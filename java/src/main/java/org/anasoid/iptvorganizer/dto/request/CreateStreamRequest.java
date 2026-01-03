package org.anasoid.iptvorganizer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * Request DTO for creating/updating streams (Live, VOD, Series)
 * Maps snake_case field names from frontend to camelCase Java properties
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStreamRequest {
    @JsonProperty("source_id")
    private Long sourceId;

    @JsonProperty("stream_id")
    private Integer streamId;

    private Integer num;

    @JsonProperty("allow_deny")
    private String allowDeny;

    private String name;

    @JsonProperty("category_id")
    private Integer categoryId;

    @JsonProperty("category_ids")
    private String categoryIds;

    @JsonProperty("is_adult")
    private Boolean isAdult;

    private String labels;
    private String data;

    @JsonProperty("added_date")
    private LocalDate addedDate;

    @JsonProperty("release_date")
    private LocalDate releaseDate;
}
