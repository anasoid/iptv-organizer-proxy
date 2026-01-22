package org.anasoid.iptvorganizer.services.xtream;

import org.anasoid.iptvorganizer.models.entity.Filter;
import org.anasoid.iptvorganizer.models.filtering.FilterConfig;

/**
 * Context holder for filtering state. Contains filter configuration and settings for a specific
 * client.
 */
public class FilterContext {
  Filter filter;
  FilterConfig filterConfig;
  boolean hideAdultContent = false;

  /** Create a new FilterContext */
  public FilterContext() {}

  /**
   * Get the filter for this context
   *
   * @return Filter or null if no filter is active
   */
  public Filter getFilter() {
    return filter;
  }

  /**
   * Set the filter for this context
   *
   * @param filter The filter to set
   */
  public void setFilter(Filter filter) {
    this.filter = filter;
  }

  /**
   * Get the filter configuration
   *
   * @return FilterConfig or null if not loaded
   */
  public FilterConfig getFilterConfig() {
    return filterConfig;
  }

  /**
   * Set the filter configuration
   *
   * @param filterConfig The configuration to set
   */
  public void setFilterConfig(FilterConfig filterConfig) {
    this.filterConfig = filterConfig;
  }

  /**
   * Check if adult content should be hidden
   *
   * @return true if adult content should be hidden
   */
  public boolean isHideAdultContent() {
    return hideAdultContent;
  }

  /**
   * Set whether adult content should be hidden
   *
   * @param hideAdultContent true to hide adult content
   */
  public void setHideAdultContent(boolean hideAdultContent) {
    this.hideAdultContent = hideAdultContent;
  }
}
