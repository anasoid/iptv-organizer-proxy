package org.anasoid.iptvorganizer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Pagination metadata for paginated responses */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationMeta {
  private int page;
  private int limit;
  private long total;
  private int totalPages;

  /** Create pagination metadata from page, limit, and total count */
  public static PaginationMeta of(int page, int limit, long total) {
    return PaginationMeta.builder()
        .page(page)
        .limit(limit)
        .total(total)
        .totalPages((int) Math.ceil((double) total / limit))
        .build();
  }
}
