package org.anasoid.iptvorganizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.anasoid.iptvorganizer.config.BooleanAsIntSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for Stream (Live, VOD, Series)
 * Boolean fields serialize as 0/1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamDTO {
    private Long id;
    private Long sourceId;
    private Integer streamId;
    private Integer num;
    private String allowDeny;
    private String name;
    private Integer categoryId;
    private String categoryIds;

    @JsonSerialize(using = BooleanAsIntSerializer.class)
    private Boolean isAdult;

    private String labels;
    private String data;
    private LocalDate addedDate;
    private LocalDate releaseDate;
    private String type;  // "live", "vod", or "series"
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
