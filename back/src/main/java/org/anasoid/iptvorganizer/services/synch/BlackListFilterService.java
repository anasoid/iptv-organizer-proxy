package org.anasoid.iptvorganizer.services.synch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.filtering.FilterAction;
import org.anasoid.iptvorganizer.models.filtering.FilterConfig;
import org.anasoid.iptvorganizer.models.filtering.FilterRule;
import org.anasoid.iptvorganizer.models.filtering.MatchCriteria;
import org.anasoid.iptvorganizer.services.FilterService;

/**
 * Service for applying blacklist filters to categories during import. Uses YAML-based filter
 * configuration from Source.blackListFilter.
 */
@Slf4j
@ApplicationScoped
public class BlackListFilterService {

  @Inject FilterService filterService;

  private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

  /**
   * Apply blacklist filter to a category based on Source's blackListFilter YAML. Returns the
   * appropriate BlackListStatus based on rule matching.
   *
   * @param category Category to evaluate
   * @param source Source containing blackListFilter YAML
   * @return BlackListStatus (DEFAULT if no rules match or filter is empty)
   */
  public Category.BlackListStatus applyBlackListFilter(Category category, Source source) {
    // If source has no blackListFilter, return DEFAULT
    if (source.getBlackListFilter() == null || source.getBlackListFilter().trim().isEmpty()) {
      return Category.BlackListStatus.DEFAULT;
    }

    // Parse YAML filter config
    FilterConfig filterConfig = parseFilterConfig(source.getBlackListFilter());
    if (filterConfig == null
        || filterConfig.getRules() == null
        || filterConfig.getRules().isEmpty()) {
      return Category.BlackListStatus.DEFAULT;
    }

    // Process rules in order (first-match-wins)
    for (FilterRule rule : filterConfig.getRules()) {
      MatchCriteria match = rule.getMatch();
      if (match == null || match.getCategories() == null) {
        continue;
      }

      // Check if rule applies to this stream type
      if (match.getStreamType() != null && !match.getStreamType().isEmpty()) {
        if (!match.getStreamType().contains(category.getType())) {
          continue;
        }
      }

      // Check if category matches rule criteria
      if (matchesCategoryCriteria(category, match)) {
        // Rule matched - apply action
        if (rule.getType() == FilterAction.INCLUDE) {
          return Category.BlackListStatus.VISIBLE;
        } else if (rule.getType() == FilterAction.EXCLUDE) {
          return Category.BlackListStatus.HIDE;
        }
      }
    }

    // No rule matched - return DEFAULT
    return Category.BlackListStatus.DEFAULT;
  }

  /**
   * Check if category matches the rule's category criteria. Delegates to FilterService for
   * consistent matching logic.
   */
  private boolean matchesCategoryCriteria(Category category, MatchCriteria match) {
    return filterService.matchesCategoryCriteria(
        category.getName(), category.getLabels(), match.getCategories());
  }

  /** Parse YAML filter configuration from string. Returns null if parsing fails. */
  private FilterConfig parseFilterConfig(String yamlContent) {
    try {
      return yamlMapper.readValue(yamlContent, FilterConfig.class);
    } catch (Exception e) {
      log.error("Failed to parse blackListFilter YAML: {}", e.getMessage());
      return null;
    }
  }
}
