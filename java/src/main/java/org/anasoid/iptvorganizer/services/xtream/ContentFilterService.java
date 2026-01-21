package org.anasoid.iptvorganizer.services.xtream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Filter;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.filtering.FilterConfig;
import org.anasoid.iptvorganizer.repositories.FilterRepository;
import org.anasoid.iptvorganizer.services.FilterService;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;

/**
 * Service for applying content filters to stream data. Orchestrates FilterService with stream
 * services to provide comprehensive filtering for categories and streams based on client settings.
 *
 * <p>Responsibilities: - Initialize filtering context per client - Apply filters to categories and
 * streams - Check individual stream access - Manage category visibility based on accessible streams
 */
@ApplicationScoped
public class ContentFilterService {

  private static final Logger LOGGER = Logger.getLogger(ContentFilterService.class.getName());

  private static final int CATEGORY_STREAM_CHECK_LIMIT = 100;

  @Inject FilterService filterService;
  @Inject CategoryService categoryService;
  @Inject LiveStreamService liveStreamService;
  @Inject VodStreamService vodStreamService;
  @Inject SeriesService seriesService;
  @Inject FilterRepository filterRepository;

  // Per-request context (thread-safe with ApplicationScoped and proper usage)
  private ThreadLocal<FilterContext> contextThreadLocal = new ThreadLocal<>();

  /**
   * Initialize filtering context for a client. Must be called before filtering operations and
   * cleaned up with {@link #clearContext()} when done.
   *
   * @param client The client to initialize filtering for
   */
  public void initializeContext(Client client) {
    FilterContext context = new FilterContext();

    // Set adult content hiding
    context.hideAdultContent = client.getHideAdultContent() != null && client.getHideAdultContent();

    // Load and cache filter if assigned
    if (client.getFilterId() != null) {
      Filter filter = filterRepository.findById(client.getFilterId());
      if (filter != null && Boolean.TRUE.equals(filter.getUseSourceFilter())) {
        context.filter = filter;
        context.filterConfig = filterService.getCachedFilterConfig(filter);
      }
    }

    contextThreadLocal.set(context);
  }

  /**
   * Clear the current filtering context. Must be called after filtering operations to prevent
   * memory leaks.
   */
  public void clearContext() {
    contextThreadLocal.remove();
  }

  /**
   * Get the current filtering context. Returns null if not initialized.
   *
   * @return Current FilterContext or null
   */
  private FilterContext getContext() {
    return contextThreadLocal.get();
  }

  /**
   * Check if filtering is enabled (filter assigned and active).
   *
   * @return true if filtering is enabled
   */
  private boolean isFilteringEnabled() {
    FilterContext ctx = getContext();
    return ctx != null && ctx.filter != null && ctx.filterConfig != null;
  }

  /**
   * Get allowed categories for a stream type. Categories are filtered based on:
   *
   * <p>1. Category allow_deny='allow' - always shown 2. Category allow_deny='deny' - always hidden
   * 3. Category allow_deny=null - shown if contains at least 1 accessible stream 4. Applied filter
   * rules (PHP-compatible)
   *
   * @param sourceId ID of the source
   * @param streamType Stream type (live, vod, series)
   * @param limit Number of categories to fetch
   * @param offset Pagination offset
   * @return Filtered list of categories
   */
  public List<Category> getAllowedCategories(
      Long sourceId, String streamType, int limit, int offset) {
    FilterContext ctx = getContext();
    if (ctx == null) {
      throw new IllegalStateException(
          "ContentFilterService not initialized. Call initializeContext() first.");
    }

    // Load categories from database
    List<Category> allCategories =
        categoryService.findBySourceAndTypePaged(sourceId, streamType, limit, offset);

    // If no filtering needed, return all
    if (!isFilteringEnabled() && !ctx.hideAdultContent) {
      return allCategories;
    }

    // Build category cache for later stream filtering
    Map<Integer, Category> categoryCache = buildCategoryCache(sourceId, streamType);

    // Filter categories
    List<Category> result = new ArrayList<>();

    for (Category category : allCategories) {
      // Explicit allow - always show
      if ("allow".equalsIgnoreCase(category.getAllowDeny())) {
        result.add(category);
        continue;
      }

      // Explicit deny - always hide
      if ("deny".equalsIgnoreCase(category.getAllowDeny())) {
        continue;
      }

      // No explicit setting - check if category has accessible streams
      if (hasAccessibleStreams(sourceId, category, streamType)) {
        result.add(category);
      }
    }

    return result;
  }

  /**
   * Check if a category has at least one accessible stream after filtering.
   *
   * @param sourceId ID of the source
   * @param category Category to check
   * @param streamType Stream type
   * @return true if category has at least one accessible stream
   */
  private boolean hasAccessibleStreams(Long sourceId, Category category, String streamType) {
    FilterContext ctx = getContext();

    // Query first N streams in category (optimization)
    List<? extends BaseStream> streams =
        getStreamsByCategory(
            sourceId, category.getExternalId(), streamType, CATEGORY_STREAM_CHECK_LIMIT);

    // Check if any stream passes filtering
    for (BaseStream stream : streams) {
      if (shouldIncludeStream(stream, category)) {
        return true; // Found at least one accessible stream
      }
    }

    return false; // No accessible streams
  }

  /**
   * Apply filtering to a stream list. Checks each stream against allow_deny rules, filter rules,
   * and adult content setting.
   *
   * @param streams Streams to filter
   * @param categoryCache Map of category ID → Category
   * @return Filtered streams
   */
  public List<BaseStream> applyFiltersToStreams(
      List<BaseStream> streams, Map<Integer, Category> categoryCache) {
    FilterContext ctx = getContext();
    if (ctx == null) {
      return streams; // No context = no filtering
    }

    List<BaseStream> filtered = new ArrayList<>();

    for (BaseStream stream : streams) {
      if (shouldIncludeStream(stream, categoryCache.get(stream.getCategoryId()))) {
        filtered.add(stream);
      }
    }

    return filtered;
  }

  /**
   * Check if a single stream should be included based on current filtering context.
   *
   * <p>Uses priority-based filtering: 1. Stream allow_deny='allow' → ALWAYS INCLUDE 2. Stream
   * allow_deny='deny' → ALWAYS EXCLUDE 3. Category allow_deny='allow' → INCLUDE 4. Category
   * allow_deny='deny' → EXCLUDE 5. Adult content filter 6. Filter rules (first-match-wins)
   *
   * @param stream Stream to check
   * @param category Stream's category
   * @return true if stream should be included
   */
  public boolean shouldIncludeStream(BaseStream stream, Category category) {
    FilterContext ctx = getContext();
    if (ctx == null) {
      return true; // No context = include all
    }

    return filterService.shouldIncludeStream(
        stream, category, ctx.filterConfig, ctx.hideAdultContent);
  }

  /**
   * Get streams by category. Helper method to load streams for category accessibility check.
   *
   * @param sourceId ID of the source
   * @param categoryId External category ID
   * @param streamType Stream type (live, vod, series)
   * @param limit Maximum number of streams to return
   * @return List of streams
   */
  private List<? extends BaseStream> getStreamsByCategory(
      Long sourceId, Integer categoryId, String streamType, int limit) {
    switch (streamType.toLowerCase()) {
      case "live":
        return liveStreamService.findBySourceAndCategory(sourceId, categoryId, limit);
      case "vod":
        return vodStreamService.findBySourceAndCategory(sourceId, categoryId, limit);
      case "series":
        return seriesService.findBySourceAndCategory(sourceId, categoryId, limit);
      default:
        return new ArrayList<>();
    }
  }

  /**
   * Build a cache of categories for efficient stream filtering.
   *
   * @param sourceId ID of the source
   * @param streamType Stream type
   * @return Map of category ID → Category
   */
  private Map<Integer, Category> buildCategoryCache(Long sourceId, String streamType) {
    List<Category> categories = categoryService.findBySourceAndType(sourceId, streamType);
    return categories.stream()
        .collect(
            Collectors.toMap(
                Category::getExternalId,
                c -> c,
                (existing, duplicate) -> existing // Handle duplicates by keeping first
                ));
  }

  /**
   * Get cached filter configuration for current context.
   *
   * @return Filter configuration or null if no filter is active
   */
  public FilterConfig getCachedFilterConfig() {
    FilterContext ctx = getContext();
    return ctx != null ? ctx.filterConfig : null;
  }

  /** Internal context class to hold filtering state for a client. */
  private static class FilterContext {
    Filter filter;
    FilterConfig filterConfig;
    boolean hideAdultContent = false;
  }
}
