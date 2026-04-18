package org.anasoid.iptvorganizer.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.exceptions.FilterException;
import org.anasoid.iptvorganizer.models.entity.Filter;
import org.anasoid.iptvorganizer.models.entity.stream.AllowDenyStatus;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.filtering.CategoryMatch;
import org.anasoid.iptvorganizer.models.filtering.ChannelMatch;
import org.anasoid.iptvorganizer.models.filtering.FilterAction;
import org.anasoid.iptvorganizer.models.filtering.FilterConfig;
import org.anasoid.iptvorganizer.models.filtering.FilterRule;
import org.anasoid.iptvorganizer.models.filtering.MatchCriteria;
import org.anasoid.iptvorganizer.repositories.FilterRepository;

@ApplicationScoped
public class FilterService extends BaseService<Filter, FilterRepository> {

  // Allow/Deny constants
  public static final String ALLOW = "allow";
  public static final String DENY = "deny";

  @Inject FilterRepository repository;

  @Inject ObjectMapper objectMapper;

  // Cache for parsed filter configurations
  private final ConcurrentHashMap<Long, FilterConfig> configCache = new ConcurrentHashMap<>();

  // Cache for compiled regex patterns to avoid recompilation
  private final ConcurrentHashMap<String, Pattern> patternCache = new ConcurrentHashMap<>();

  @Override
  protected FilterRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(Filter filter) {
    if (filter.getName() == null || filter.getName().isBlank()) {
      throw new IllegalArgumentException("Name is required");
    }
    if (filter.getFilterConfig() == null || filter.getFilterConfig().isBlank()) {
      throw new IllegalArgumentException("Filter config is required");
    }

    // Validate YAML on creation
    try {
      parseFilterConfig(filter.getFilterConfig());
    } catch (Exception e) {
      throw new FilterException("Invalid filter configuration: " + e.getMessage(), e);
    }

    return repository.insert(filter);
  }

  /** Parse YAML filter configuration string into FilterConfig object */
  public FilterConfig parseFilterConfig(String yaml) {
    try {
      ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
      return yamlMapper.readValue(yaml, FilterConfig.class);
    } catch (Exception e) {
      throw new FilterException("Failed to parse filter YAML configuration", e);
    }
  }

  /** Get cached filter configuration, parsing if not already cached */
  public FilterConfig getCachedFilterConfig(Filter filter) {
    return configCache.computeIfAbsent(
        filter.getId(),
        id -> {
          try {
            return parseFilterConfig(filter.getFilterConfig());
          } catch (Exception e) {
            throw new FilterException("Failed to parse filter config for ID: " + id, e);
          }
        });
  }

  /** Invalidate cache entries when filter is updated or deleted */
  public void invalidateCache(Long filterId) {
    configCache.remove(filterId);
  }

  /** Apply filter to a list of items */
  public <T> List<T> applyFilter(Filter filter, List<T> items) {
    FilterConfig config = getCachedFilterConfig(filter);
    List<T> filtered = new ArrayList<>();
    for (T item : items) {
      if (matches(config, item)) {
        filtered.add(item);
      }
    }
    return filtered;
  }

  /** Check if an item matches the filter configuration */
  public <T> boolean matches(FilterConfig config, T item) {
    if (config == null || config.getRules() == null || config.getRules().isEmpty()) {
      return true; // No rules = accept all
    }

    // Apply all rules - item must pass all rules
    for (FilterRule rule : config.getRules()) {
      if (!matchesRule(rule, item)) {
        return false;
      }
    }
    return true;
  }

  /** Check if an item matches a single filter rule */
  private <T> boolean matchesRule(FilterRule rule, T item) {
    try {
      // Get field value via reflection
      Object value = getFieldValue(item, rule.getField().getFieldName());

      // If pattern is provided, use regex matching
      if (rule.getPattern() != null && !rule.getPattern().isBlank()) {
        Pattern pattern = patternCache.computeIfAbsent(rule.getPattern(), Pattern::compile);
        boolean matches = pattern.matcher(value.toString()).matches();

        // Apply include/exclude logic
        if (rule.getAction().name().equals("INCLUDE")) {
          return matches;
        } else {
          return !matches;
        }
      }

      // If value is provided, use equality matching
      if (rule.getValue() != null) {
        boolean matches = value.equals(rule.getValue());

        if (rule.getAction().name().equals("INCLUDE")) {
          return matches;
        } else {
          return !matches;
        }
      }

      // No pattern or value = accept
      return true;
    } catch (Exception e) {
      // If field access fails, treat as non-match
      return false;
    }
  }

  /** Get field value from object using reflection */
  private <T> Object getFieldValue(T item, String fieldName) throws Exception {
    String getterName = "get" + capitalize(fieldName);
    Method getter = item.getClass().getMethod(getterName);
    return getter.invoke(item);
  }

  /** Capitalize first letter of field name */
  private String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }

  // ==================== NEW PHP-COMPATIBLE FILTERING METHODS ====================

  /**
   * Pattern matching with wildcard support (* and ? characters). Falls back to case-insensitive
   * substring matching if no wildcards.
   *
   * @param text Text to match against
   * @param pattern Pattern with optional wildcards (* = any chars, ? = single char)
   * @return true if text matches pattern
   */
  protected boolean matchesPattern(String text, String pattern) {
    // If pattern is null or empty, no filter criteria = always matches
    if (pattern == null || pattern.isEmpty()) {
      return true;
    }

    // If text is null or empty, no text to filter = always matches
    // (represents "no filtering criteria")
    if (text == null || text.isEmpty()) {
      return true;
    }

    text = text.toLowerCase();
    pattern = pattern.toLowerCase();

    // Check if pattern contains wildcards
    if (pattern.contains("*") || pattern.contains("?")) {
      // Convert wildcard pattern to regex
      // First escape all regex special chars except * and ?
      String regex =
          pattern
              .replace("\\", "\\\\") // Escape backslash first (must be before other escapes)
              .replace(".", "\\.") // Escape dots
              .replace("|", "\\|") // Escape pipes
              .replace("^", "\\^") // Escape caret
              .replace("$", "\\$") // Escape dollar
              .replace("+", "\\+") // Escape plus
              .replace("[", "\\[") // Escape opening bracket
              .replace("]", "\\]") // Escape closing bracket
              .replace("{", "\\{") // Escape opening brace
              .replace("}", "\\}") // Escape closing brace
              .replace("(", "\\(") // Escape opening paren
              .replace(")", "\\)") // Escape closing paren
              .replace("*", ".*") // Replace * with .*
              .replace("?", "."); // Replace ? with .
      return text.matches(regex);
    }

    // Substring matching (case-insensitive)
    return text.contains(pattern);
  }

  /**
   * Check if text matches ANY pattern in list (OR logic).
   *
   * @param text Text to match
   * @param patterns List of patterns
   * @return true if text matches any pattern, false if patterns is empty
   */
  protected boolean matchesAnyPattern(String text, List<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return false;
    }
    return patterns.stream().anyMatch(p -> matchesPattern(text, p));
  }

  /**
   * Parse comma-separated labels into lowercase list.
   *
   * @param labelString Comma-separated label string
   * @return List of lowercase labels
   */
  protected List<String> parseLabels(String labelString) {
    if (labelString == null || labelString.isEmpty()) {
      return Collections.emptyList();
    }
    return Arrays.stream(labelString.split(","))
        .map(s -> s.trim().toLowerCase())
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }

  /**
   * Helper to check if list is empty.
   *
   * @param list List to check
   * @return true if list is null or empty
   */
  protected boolean isEmpty(List<String> list) {
    return list == null || list.isEmpty();
  }

  /**
   * Check if stream matches channel criteria.
   *
   * <p>Matching Rules: - by_name: ANY pattern matches (OR logic) - by_labels: ANY pattern matches
   * ANY label (OR logic) - Both name and labels criteria must match if both specified (AND)
   *
   * @param channelName Stream name
   * @param channelLabelsStr Comma-separated channel labels
   * @param criteria Channel matching criteria
   * @return true if stream matches criteria
   */
  protected boolean matchesChannelCriteria(
      String channelName, String channelLabelsStr, ChannelMatch criteria) {
    channelName = channelName != null ? channelName.toLowerCase() : "";
    List<String> channelLabels = parseLabels(channelLabelsStr);

    // Check by_name: ANY pattern matches (OR logic)
    boolean nameMatches = true; // No name criteria = matches
    if (!isEmpty(criteria.getByName())) {
      nameMatches = matchesAnyPattern(channelName, criteria.getByName());
    }

    // Check by_labels: ANY pattern matches ANY label (OR logic)
    boolean labelsMatch = true; // No label criteria = matches
    if (!isEmpty(criteria.getByLabels())) {
      labelsMatch = false;
      for (String pattern : criteria.getByLabels()) {
        for (String label : channelLabels) {
          if (matchesPattern(label, pattern)) {
            labelsMatch = true;
            break;
          }
        }
        if (labelsMatch) break;
      }
    }

    // Both criteria must match (AND)
    return nameMatches && labelsMatch;
  }

  /**
   * Check if category matches criteria.
   *
   * <p>Matching Rules: - by_name: ANY pattern matches (OR logic) - by_labels: ANY pattern matches
   * ANY label (OR logic) - Both name and labels criteria must match if both specified (AND)
   *
   * @param categoryName Category name
   * @param categoryLabelsStr Comma-separated category labels
   * @param criteria Category matching criteria
   * @return true if category matches criteria
   */
  public boolean matchesCategoryCriteria(
      String categoryName, String categoryLabelsStr, CategoryMatch criteria) {
    categoryName = categoryName != null ? categoryName.toLowerCase() : "";
    List<String> categoryLabels = parseLabels(categoryLabelsStr);

    // Check by_name: ANY pattern matches (OR logic)
    boolean nameMatches = true; // No name criteria = matches
    if (!isEmpty(criteria.getByName())) {
      nameMatches = matchesAnyPattern(categoryName, criteria.getByName());
    }

    // Check by_labels: ANY pattern matches ANY label (OR logic)
    boolean labelsMatch = true; // No label criteria = matches
    if (!isEmpty(criteria.getByLabels())) {
      labelsMatch = false;
      for (String pattern : criteria.getByLabels()) {
        for (String label : categoryLabels) {
          if (matchesPattern(label, pattern)) {
            labelsMatch = true;
            break;
          }
        }
        if (labelsMatch) break;
      }
    }

    // Both criteria must match (AND)
    return nameMatches && labelsMatch;
  }

  /**
   * Check if stream matches rule criteria (for STREAM filtering).
   *
   * <p>Logic (PHP-compatible): - If rule has BOTH channel and category criteria → BOTH must match
   * (AND) - If rule has only channel criteria → only channel must match - If rule has only category
   * criteria → only category must match - If rule has neither → false (doesn't match)
   *
   * <p>OPTIMIZATION: Category match results are cached within the request (via categoryMatchCache
   * param) and reused across all streams in the same category. This significantly improves
   * performance when most filtering is category-based, as is typical in IPTV scenarios.
   *
   * @param stream Stream to check
   * @param category Category of the stream
   * @param match Match criteria
   * @param categoryMatchCache Request-scoped cache for category match results (reused during same
   *     request)
   * @return true if stream matches rule
   */
  protected boolean matchStream(
      BaseStream stream,
      Category category,
      MatchCriteria match,
      Map<String, Boolean> categoryMatchCache) {
    boolean hasChannelCriteria =
        (match.getChannels() != null)
            && (!isEmpty(match.getChannels().getByName())
                || !isEmpty(match.getChannels().getByLabels()));

    boolean hasCategoryCriteria =
        (match.getCategories() != null)
            && (!isEmpty(match.getCategories().getByName())
                || !isEmpty(match.getCategories().getByLabels()));

    // If both channel and category criteria exist, both must match (AND)
    if (hasChannelCriteria && hasCategoryCriteria) {
      // Evaluate channel criteria
      boolean channelMatches =
          matchesChannelCriteria(stream.getName(), stream.getLabels(), match.getChannels());
      if (!channelMatches) {
        return false; // Short-circuit: channel doesn't match
      }

      // Evaluate category criteria with request-scoped caching
      boolean categoryMatches =
          matchesCategoryWithCache(
              stream.getCategoryId(), category, match.getCategories(), categoryMatchCache);
      return categoryMatches;
    }

    // If only channel criteria exists
    if (hasChannelCriteria) {
      return matchesChannelCriteria(stream.getName(), stream.getLabels(), match.getChannels());
    }

    // If only category criteria exists - use request cache
    if (hasCategoryCriteria) {
      return matchesCategoryWithCache(
          stream.getCategoryId(), category, match.getCategories(), categoryMatchCache);
    }

    return false;
  }

  /**
   * Evaluate category matching with request-scoped caching. Category results are cached because
   * they're often shared across many streams (all streams in a category have the same category
   * match result). Cache is request-scoped and should be cleared after the request completes.
   *
   * @param categoryId External category ID for cache key
   * @param category Category object to match
   * @param criteria Category matching criteria
   * @param categoryMatchCache Request-scoped cache map (typically a HashMap, not thread-safe)
   * @return true if category matches criteria
   */
  private boolean matchesCategoryWithCache(
      Integer categoryId,
      Category category,
      CategoryMatch criteria,
      Map<String, Boolean> categoryMatchCache) {
    if (category == null) {
      return false;
    }

    // If categoryId is null, evaluate without caching (e.g., in tests)
    if (categoryId == null) {
      return matchesCategoryCriteria(category.getName(), category.getLabels(), criteria);
    }

    String cacheKey = categoryId + "#" + System.identityHashCode(criteria);

    return categoryMatchCache.computeIfAbsent(
        cacheKey,
        key -> matchesCategoryCriteria(category.getName(), category.getLabels(), criteria));
  }

  /**
   * Apply priority-based filtering to streams (PHP-compatible).
   *
   * <p>Priority Order (highest to lowest): 1. Stream allow_deny='allow' → ALWAYS INCLUDE 2. Stream
   * allow_deny='deny' → ALWAYS EXCLUDE 3. Category allow_deny='allow' → INCLUDE (unless stream has
   * deny) 4. Category allow_deny='deny' → EXCLUDE (unless stream has allow) 5. Adult content filter
   * (if hideAdultContent=true) 6. Filter rules (first-match-wins)
   *
   * @param streams Array of streams
   * @param categoryCache Map of category ID → Category
   * @param hideAdultContent Whether to hide adult content
   * @return Filtered streams
   */
  public List<BaseStream> applyToStreams(
      List<BaseStream> streams, Map<Integer, Category> categoryCache, boolean hideAdultContent) {
    List<BaseStream> allowed = new ArrayList<>();
    List<BaseStream> toFilter = new ArrayList<>();

    // Phase 1: Separate streams by allow_deny priority
    for (BaseStream stream : streams) {
      AllowDenyStatus streamAllowDeny = stream.getAllowDeny();
      Category category = categoryCache.get(stream.getCategoryId());
      AllowDenyStatus categoryAllowDeny = category != null ? category.getAllowDeny() : null;

      // Priority 1: Stream allow_deny='allow' - ALWAYS INCLUDE
      if (streamAllowDeny == AllowDenyStatus.ALLOW) {
        allowed.add(stream);
        continue;
      }

      // Priority 2: Stream allow_deny='deny' - ALWAYS EXCLUDE
      if (streamAllowDeny == AllowDenyStatus.DENY) {
        continue; // Skip this stream
      }

      // Priority 3: Category allow_deny='allow' - INCLUDE STREAM
      if (categoryAllowDeny == AllowDenyStatus.ALLOW) {
        allowed.add(stream);
        continue;
      }

      // Priority 4: Category allow_deny='deny' - EXCLUDE STREAM
      if (categoryAllowDeny == AllowDenyStatus.DENY) {
        continue; // Skip this stream
      }

      // No explicit override - needs filtering
      toFilter.add(stream);
    }

    // Phase 2: Apply adult content filter
    List<BaseStream> filtered = filterAdultContent(toFilter, hideAdultContent);

    // Phase 3: Apply include/exclude rules
    filtered = applyFilterRules(filtered, categoryCache);

    // Combine: explicitly allowed streams + filter-passed streams
    allowed.addAll(filtered);
    return allowed;
  }

  /**
   * Filter out adult content streams.
   *
   * @param streams Streams to filter
   * @param hideAdultContent Whether to hide adult content
   * @return Filtered streams
   */
  protected List<BaseStream> filterAdultContent(
      List<BaseStream> streams, boolean hideAdultContent) {
    if (!hideAdultContent) {
      return streams;
    }
    return streams.stream()
        .filter(s -> !Boolean.TRUE.equals(s.getIsAdult()))
        .collect(Collectors.toList());
  }

  /**
   * Apply include/exclude rules (PHP-compatible).
   *
   * <p>First matching rule wins: - If type is "include": if stream matches → ACCEPT and STOP - If
   * type is "exclude": if stream matches → REJECT and STOP
   *
   * <p>If stream doesn't match ANY rule: - If there are include rules → REJECT (must match an
   * include rule) - If only exclude rules → ACCEPT (not explicitly excluded)
   *
   * @param streams Streams to filter
   * @param categoryCache Map of category ID → Category
   * @return Filtered streams
   */
  protected List<BaseStream> applyFilterRules(
      List<BaseStream> streams, Map<Integer, Category> categoryCache) {
    // This requires the filter config - for now, return as-is
    // It will be called from a context that has access to filter
    return streams;
  }

  /**
   * Check if a single stream should be included based on filters.
   *
   * <p>Used for efficient filtering of individual streams or small batches. For batch filtering
   * with category caching, use shouldIncludeStream(stream, category, config, hideAdultContent,
   * categoryMatchCache) instead.
   *
   * @param stream Stream to check
   * @param category Stream's category
   * @param config Filter configuration (null = no filtering)
   * @param hideAdultContent Whether to hide adult content
   * @return true if stream should be included
   */
  public boolean shouldIncludeStream(
      BaseStream stream, Category category, FilterConfig config, boolean hideAdultContent) {
    return shouldIncludeStream(stream, category, config, hideAdultContent, new HashMap<>());
  }

  /**
   * Check if a single stream should be included based on filters with request-scoped category
   * caching.
   *
   * <p>Category match results are cached during the request to avoid redundant matching when
   * filtering multiple streams from the same category.
   *
   * @param stream Stream to check
   * @param category Stream's category
   * @param config Filter configuration (null = no filtering)
   * @param hideAdultContent Whether to hide adult content
   * @param categoryMatchCache Request-scoped cache for category match results
   * @return true if stream should be included
   */
  public boolean shouldIncludeStream(
      BaseStream stream,
      Category category,
      FilterConfig config,
      boolean hideAdultContent,
      Map<String, Boolean> categoryMatchCache) {
    // Priority 1: Stream allow_deny='allow' - ALWAYS INCLUDE
    if (stream.getAllowDeny() == AllowDenyStatus.ALLOW) {
      return true;
    }

    // Priority 2: Stream allow_deny='deny' - ALWAYS EXCLUDE
    if (stream.getAllowDeny() == AllowDenyStatus.DENY) {
      return false;
    }

    // Priority 3: Category allow_deny='allow' - INCLUDE
    if (category != null && category.getAllowDeny() == AllowDenyStatus.ALLOW) {
      return true;
    }

    // Priority 4: Category allow_deny='deny' - EXCLUDE
    if (category != null && category.getAllowDeny() == AllowDenyStatus.DENY) {
      return false;
    }

    // Priority 5: Adult content filter
    if (hideAdultContent && Boolean.TRUE.equals(stream.getIsAdult())) {
      return false;
    }

    // Priority 6: Filter rules
    if (config != null && config.getRules() != null && !config.getRules().isEmpty()) {
      List<FilterRule> rules = config.getRules();

      // Check if there are any include rules
      boolean hasIncludeRules = rules.stream().anyMatch(r -> r.getType() == FilterAction.INCLUDE);

      // Process rules in order - first matching rule wins
      for (FilterRule rule : rules) {
        MatchCriteria match = rule.getMatch();
        if (match != null && matchStream(stream, category, match, categoryMatchCache)) {
          // First matching rule wins
          return rule.getType() == FilterAction.INCLUDE;
        }
      }

      // No rule matched - apply default behavior
      // If there are include rules, reject (must match an include rule)
      // If only exclude rules, accept (not explicitly excluded)
      return !hasIncludeRules;
    }

    // No filters applied - include by default
    return true;
  }

  /**
   * Filter categories using rules (PHP-compatible).
   *
   * <p>KEY DIFFERENCES FROM STREAM FILTERING: 1. Only uses rules that have match.categories
   * (ignores channel-only rules) 2. Filters by stream_type if specified in rule 3. DEFAULT
   * BEHAVIOR: Categories are KEPT unless explicitly excluded (Unlike streams where default depends
   * on hasIncludeRules)
   *
   * @param categories Array of categories
   * @param type Stream type (live, vod, series)
   * @param config Filter configuration (null = no filtering)
   * @return Filtered categories
   */
  public List<Category> filterCategories(
      List<Category> categories, String type, FilterConfig config) {
    if (config == null || config.getRules() == null || config.getRules().isEmpty()) {
      return categories;
    }

    List<FilterRule> rules = config.getRules();

    // Filter rules to only those matching the category stream type and with category criteria
    List<FilterRule> typeRules =
        rules.stream()
            .filter(
                rule -> {
                  MatchCriteria match = rule.getMatch();
                  if (match == null) {
                    return false;
                  }

                  // Skip if rule has stream type requirement that doesn't match
                  if (type != null
                      && match.getStreamType() != null
                      && !match.getStreamType().isEmpty()) {
                    if (!match.getStreamType().contains(type)) {
                      return false;
                    }
                  }

                  // Only include rules that have category criteria
                  return match.getCategories() != null
                      && (!isEmpty(match.getCategories().getByName())
                          || !isEmpty(match.getCategories().getByLabels()));
                })
            .collect(Collectors.toList());

    // If no matching rules, return all categories
    if (typeRules.isEmpty()) {
      return categories;
    }

    // Filter categories respecting rule order
    return categories.stream()
        .filter(
            category -> {
              // Process rules in order
              for (FilterRule rule : typeRules) {
                MatchCriteria match = rule.getMatch();

                // Check if category matches this rule
                if (matchesCategoryCriteria(
                    category.getName(), category.getLabels(), match.getCategories())) {
                  // If matches and type is include → ACCEPT
                  if (rule.getType() == FilterAction.INCLUDE) {
                    return true;
                  }
                  // If matches and type is exclude → REJECT
                  if (rule.getType() == FilterAction.EXCLUDE) {
                    return false;
                  }
                }
              }

              // Category didn't match any rule → KEEP
              // THIS IS DIFFERENT FROM STREAM FILTERING!
              // Categories are always visible unless explicitly excluded
              return true;
            })
        .collect(Collectors.toList());
  }
}
