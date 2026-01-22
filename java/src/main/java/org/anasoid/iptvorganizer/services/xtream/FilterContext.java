package org.anasoid.iptvorganizer.services.xtream;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.anasoid.iptvorganizer.models.entity.Filter;
import org.anasoid.iptvorganizer.models.filtering.FilterConfig;

/**
 * Context holder for filtering state. Contains filter configuration and settings for a specific
 * client.
 */
@Getter
@Setter
@NoArgsConstructor
public class FilterContext {
  private Filter filter;
  private FilterConfig filterConfig;
  private boolean hideAdultContent = false;
}
