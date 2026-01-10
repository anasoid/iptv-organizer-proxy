package org.anasoid.iptvorganizer.models.stream;

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
public class Category extends SourcedEntity {
  private Integer categoryId;
  private String categoryName;
  private String categoryType;
  private String allowDeny;
  private Integer parentId;
  private String labels;
}
