package org.anasoid.iptvorganizer.models.entity.stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.anasoid.iptvorganizer.models.entity.BaseEntity;

/**
 * Base class for all entities that belong to a source and have ordering. This includes both
 * categories and streams.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class SourcedEntity extends BaseEntity {
  /** ID of the source this entity belongs to */
  private Long sourceId;

  /** Stream ID from the source provider */
  private Integer externalId;

  /** Ordering number within the source */
  private Integer num;
}
