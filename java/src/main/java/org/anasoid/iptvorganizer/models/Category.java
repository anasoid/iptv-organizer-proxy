package org.anasoid.iptvorganizer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Category extends BaseEntity {
    private Long sourceId;
    private Integer categoryId;
    private String categoryName;
    private String categoryType;
    private Integer num;
    private String allowDeny;
    private Integer parentId;
    private String labels;
}
