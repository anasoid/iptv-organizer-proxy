package org.anasoid.iptvorganizer.models.stream;

import java.time.LocalDate;
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
public class LiveStream extends BaseEntity implements StreamLike {
  private Long sourceId;
  private Integer streamId;
  private Integer num;
  private String allowDeny;
  private String name;
  private Integer categoryId;
  private String categoryIds;
  private Boolean isAdult;
  private String labels;
  private String data;
  private LocalDate addedDate;
  private LocalDate releaseDate;
}
