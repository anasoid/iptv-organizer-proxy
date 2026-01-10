package org.anasoid.iptvorganizer.models.stream;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.anasoid.iptvorganizer.models.BaseEntity;

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
