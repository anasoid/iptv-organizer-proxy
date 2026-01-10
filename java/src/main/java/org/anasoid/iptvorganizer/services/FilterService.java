package org.anasoid.iptvorganizer.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.anasoid.iptvorganizer.exceptions.FilterException;
import org.anasoid.iptvorganizer.models.Filter;
import org.anasoid.iptvorganizer.models.filtering.FilterConfig;
import org.anasoid.iptvorganizer.models.filtering.FilterRule;
import org.anasoid.iptvorganizer.models.stream.*;
import org.anasoid.iptvorganizer.repositories.FilterRepository;
import org.anasoid.iptvorganizer.repositories.stream.*;

@ApplicationScoped
public class FilterService extends BaseService<Filter, FilterRepository> {

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
  public Uni<Long> create(Filter filter) {
    if (filter.getName() == null || filter.getName().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Name is required"));
    }
    if (filter.getFilterConfig() == null || filter.getFilterConfig().isBlank()) {
      return Uni.createFrom().failure(new IllegalArgumentException("Filter config is required"));
    }

    // Validate YAML on creation
    try {
      parseFilterConfig(filter.getFilterConfig());
    } catch (Exception e) {
      return Uni.createFrom()
          .failure(new FilterException("Invalid filter configuration: " + e.getMessage(), e));
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

  /** Apply filter to a stream of items */
  public <T> Multi<T> applyFilter(Filter filter, Multi<T> items) {
    FilterConfig config = getCachedFilterConfig(filter);
    return items.filter(item -> matches(config, item));
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
}
