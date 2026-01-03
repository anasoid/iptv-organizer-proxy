package org.anasoid.iptvorganizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.anasoid.iptvorganizer.models.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO for Category
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryDTO {
    private Long id;
    private Long sourceId;
    private Integer categoryId;
    private String categoryName;
    private String categoryType;
    private Integer num;
    private String allowDeny;
    private Integer parentId;
    private String labels;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convert entity to DTO
     */
    public static CategoryDTO fromEntity(Category entity) {
        if (entity == null) return null;

        return CategoryDTO.builder()
            .id(entity.getId())
            .sourceId(entity.getSourceId())
            .categoryId(entity.getCategoryId())
            .categoryName(entity.getCategoryName())
            .categoryType(entity.getCategoryType())
            .num(entity.getNum())
            .allowDeny(entity.getAllowDeny())
            .parentId(entity.getParentId())
            .labels(entity.getLabels())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
