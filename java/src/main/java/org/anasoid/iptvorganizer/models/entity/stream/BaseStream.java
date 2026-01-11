package org.anasoid.iptvorganizer.models.entity.stream;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base class for all stream types (LiveStream, VodStream, Series). Extends SourcedEntity and adds
 * category relationship and common stream attributes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public abstract class BaseStream extends SourcedEntity {
  /** ID of the category this stream belongs to */
  private Integer categoryId;

  /** Allow/deny status for filtering */
  private String allowDeny;

  /** Stream name/title */
  private String name;

  /** Comma-separated category IDs (for multi-category support) */
  private String categoryIds;

  /** Whether this stream contains adult content */
  private Boolean isAdult;

  /** Extracted labels for filtering and search */
  private String labels;

  /** Raw JSON data from the source API */
  private String data;

  /** Date when the stream was added */
  private LocalDate addedDate;

  /** Release date of the content */
  private LocalDate releaseDate;
}
