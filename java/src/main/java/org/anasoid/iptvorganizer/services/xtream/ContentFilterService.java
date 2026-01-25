package org.anasoid.iptvorganizer.services.xtream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@ApplicationScoped
public class ContentFilterService {

  private static final int CATEGORY_STREAM_CHECK_LIMIT = 100;

  @Inject FilterService filterService;
  @Inject CategoryService categoryService;
  @Inject LiveStreamService liveStreamService;
  @Inject VodStreamService vodStreamService;
  @Inject SeriesService seriesService;
  @Inject FilterRepository filterRepository;

  /**
   * Build filtering context for a client. Creates a context with the client's filter settings and
   * adult content preferences.
   *
   * @param client The client to build filtering context for
   * @return FilterContext with client's filtering configuration
   */
  public FilterContext buildFilterContext(Client client) {
    FilterContext context = new FilterContext();

    // Set adult content hiding
    context.setHideAdultContent(
        client.getHideAdultContent() != null && client.getHideAdultContent());

    // Load and cache filter if assigned
    if (client.getFilterId() != null) {
      Filter filter = filterRepository.findById(client.getFilterId());
      if (filter != null && Boolean.TRUE.equals(filter.getUseSourceFilter())) {
        context.setFilter(filter);
        context.setFilterConfig(filterService.getCachedFilterConfig(filter));
      }
    }

    return context;
  }

  /**
   * Check if filtering is enabled (filter assigned and active).
   *
   * @param context The filtering context
   * @return true if filtering is enabled
   */
  private boolean isFilteringEnabled(FilterContext context) {
    return context != null && context.getFilter() != null && context.getFilterConfig() != null;
  }

  /**
   * Get allowed categories for a stream type. Categories are filtered based on:
   *
   * <p>1. Category allow_deny='allow' - always shown 2. Category allow_deny='deny' - always hidden
   * 3. Category allow_deny=null - filtered by YAML rules, then shown if has ≥1 accessible stream 4.
   * Applied filter rules (PHP-compatible)
   *
   * @param context The filtering context
   * @param sourceId ID of the source
   * @param streamType Stream type (live, vod, series)
   * @return Filtered list of categories
   */
  public List<Category> getAllowedCategories(
      FilterContext context, Long sourceId, String streamType) {
    if (context == null) {
      throw new IllegalArgumentException("FilterContext cannot be null");
    }

    // Load categories from database
    List<Category> allCategories = categoryService.findBySourceAndType(sourceId, streamType);

    // If no filtering needed, return all
    if (!isFilteringEnabled(context) && !context.isHideAdultContent()) {
      return allCategories;
    }

    // Separate explicitly allowed/denied categories from neutral ones
    List<Category> allowedByDefault = new ArrayList<>();
    List<Category> neutralCategories = new ArrayList<>();

    for (Category category : allCategories) {
      if ("allow".equalsIgnoreCase(category.getAllowDeny())) {
        allowedByDefault.add(category);
      } else if (!"deny".equalsIgnoreCase(category.getAllowDeny())) {
        neutralCategories.add(category);
      }
    }

    // Apply YAML filter rules to neutral categories
    List<Category> yamlFilteredCategories = neutralCategories;
    if (isFilteringEnabled(context)) {
      yamlFilteredCategories =
          filterService.filterCategories(neutralCategories, streamType, context.getFilterConfig());
    }

    // Build category cache for stream accessibility checks
    Map<Integer, Category> categoryCache = buildCategoryCache(sourceId, streamType);

    // Evaluate each neutral category individually
    List<Category> result = new ArrayList<>(allowedByDefault);
    for (Category category : yamlFilteredCategories) {
      if (shouldIncludeCategory(context, category, sourceId, streamType, categoryCache)) {
        result.add(category);
      }
    }
    return result;
  }

  /**
   * Check if a single category should be included based on filtering rules.
   *
   * <p>Filtering logic (evaluated in order): 1. Category allow_deny='allow' → ALWAYS INCLUDE 2.
   * Category allow_deny='deny' → ALWAYS EXCLUDE 3. For neutral categories (no explicit allow_deny):
   * - Check if category has at least one accessible stream
   *
   * <p>Note: YAML filter rules are applied during batch loading in getAllowedCategories().
   *
   * @param context The filtering context
   * @param category The category to evaluate
   * @param sourceId ID of the source
   * @param streamType Stream type (live, vod, series)
   * @param categoryCache Map of category ID → Category for lookups
   * @return true if category should be included
   */
  public boolean shouldIncludeCategory(
      FilterContext context,
      Category category,
      Long sourceId,
      String streamType,
      Map<Integer, Category> categoryCache) {
    // Priority 1: Explicit allow always includes
    if ("allow".equalsIgnoreCase(category.getAllowDeny())) {
      return true;
    }

    // Priority 2: Explicit deny always excludes
    if ("deny".equalsIgnoreCase(category.getAllowDeny())) {
      return false;
    }

    // Priority 3: Neutral category - check if it has accessible streams
    return hasAccessibleStreams(context, sourceId, category, streamType);
  }

  /**
   * Check if a category has at least one accessible stream after filtering.
   *
   * @param context The filtering context
   * @param sourceId ID of the source
   * @param category Category to check
   * @param streamType Stream type
   * @return true if category has at least one accessible stream
   */
  private boolean hasAccessibleStreams(
      FilterContext context, Long sourceId, Category category, String streamType) {
    // Query first N streams in category (optimization)
    List<? extends BaseStream> streams =
        getStreamsByCategory(
            sourceId, category.getExternalId(), streamType, CATEGORY_STREAM_CHECK_LIMIT);

    // Check if any stream passes filtering
    for (BaseStream stream : streams) {
      if (shouldIncludeStream(context, stream, category)) {
        return true; // Found at least one accessible stream
      }
    }

    return false; // No accessible streams
  }

  /**
   * Apply filtering to a stream list. Checks each stream against allow_deny rules, filter rules,
   * and adult content setting.
   *
   * @param context The filtering context
   * @param streams Streams to filter
   * @param categoryCache Map of category ID → Category
   * @return Filtered streams
   */
  public List<BaseStream> applyFiltersToStreams(
      FilterContext context, List<BaseStream> streams, Map<Integer, Category> categoryCache) {
    if (context == null) {
      return streams; // No context = no filtering
    }

    List<BaseStream> filtered = new ArrayList<>();

    for (BaseStream stream : streams) {
      if (shouldIncludeStream(context, stream, categoryCache.get(stream.getCategoryId()))) {
        filtered.add(stream);
      }
    }

    return filtered;
  }

  /**
   * Check if a single stream should be included based on filtering context.
   *
   * <p>Uses priority-based filtering: 1. Stream allow_deny='allow' → ALWAYS INCLUDE 2. Stream
   * allow_deny='deny' → ALWAYS EXCLUDE 3. Category allow_deny='allow' → INCLUDE 4. Category
   * allow_deny='deny' → EXCLUDE 5. Adult content filter 6. Filter rules (first-match-wins)
   *
   * @param context The filtering context
   * @param stream Stream to check
   * @param category Stream's category
   * @return true if stream should be included
   */
  public boolean shouldIncludeStream(FilterContext context, BaseStream stream, Category category) {
    if (context == null) {
      return false; // No context = include all
    }

    return filterService.shouldIncludeStream(
        stream, category, context.getFilterConfig(), context.isHideAdultContent());
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
   * Get cached filter configuration for the given context.
   *
   * @param context The filtering context
   * @return Filter configuration or null if no filter is active
   */
  public FilterConfig getCachedFilterConfig(FilterContext context) {
    return context != null ? context.getFilterConfig() : null;
  }
}
