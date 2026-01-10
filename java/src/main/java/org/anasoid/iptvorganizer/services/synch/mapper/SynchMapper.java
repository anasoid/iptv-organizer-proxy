package org.anasoid.iptvorganizer.services.synch.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.java.Log;
import org.anasoid.iptvorganizer.helper.LabelExtractorHelper;
import org.anasoid.iptvorganizer.models.Source;
import org.anasoid.iptvorganizer.models.stream.Category;
import org.anasoid.iptvorganizer.models.stream.LiveStream;
import org.anasoid.iptvorganizer.models.stream.Series;
import org.anasoid.iptvorganizer.models.stream.VodStream;

@ApplicationScoped
@Log
public class SynchMapper {

  @Inject LabelExtractorHelper labelExtractor;

  /** Map API response data to Category objects */
  public CategoryMappingResult mapCategoryData(
      Source source, List<Map> categoryMaps, String categoryType) {
    List<Category> categories = new ArrayList<>();
    Set<Integer> fetchedCategoryIds = new HashSet<>();
    AtomicInteger num = new AtomicInteger(1);

    for (Map catData : categoryMaps) {
      Category category = new Category();
      category.setSourceId(source.getId());
      category.setExternalId(getIntValue(catData, "category_id"));
      category.setName(getStringValue(catData, "category_name"));
      category.setType(categoryType);
      category.setNum(num.getAndIncrement());
      category.setParentId(getIntValue(catData, "parent_id"));
      category.setLabels(labelExtractor.extractLabels(category.getName()));

      categories.add(category);
      fetchedCategoryIds.add(category.getExternalId());
    }

    log.info("Fetched " + categories.size() + " " + categoryType + " categories from API");
    return new CategoryMappingResult(categories, fetchedCategoryIds);
  }

  /** Helper class to hold category mapping results */
  public static class CategoryMappingResult {
    public List<Category> categories;
    public Set<Integer> fetchedIds;

    public CategoryMappingResult(List<Category> categories, Set<Integer> fetchedIds) {
      this.categories = categories;
      this.fetchedIds = fetchedIds;
    }
  }

  /** Map API response to LiveStream entity */
  public LiveStream mapToLiveStream(Source source, Map<?, ?> data) {
    return LiveStream.builder()
        .sourceId(source.getId())
        .externalId(getIntValue(data, "stream_id"))
        .num(getIntValue(data, "num"))
        .name(getStringValue(data, "name"))
        .categoryId(getIntValue(data, "category_id"))
        .isAdult(getBooleanValue(data, "is_adult"))
        .labels(labelExtractor.extractLabels(getStringValue(data, "name")))
        .data(convertMapToJson(data))
        .build();
  }

  /** Map API response to VodStream entity */
  public VodStream mapToVodStream(Source source, Map<?, ?> data) {
    return VodStream.builder()
        .sourceId(source.getId())
        .externalId(getIntValue(data, "stream_id"))
        .num(getIntValue(data, "num"))
        .name(getStringValue(data, "name"))
        .categoryId(getIntValue(data, "category_id"))
        .isAdult(getBooleanValue(data, "is_adult"))
        .labels(labelExtractor.extractLabels(getStringValue(data, "name")))
        .data(convertMapToJson(data))
        .build();
  }

  /** Map API response to Series entity */
  public Series mapToSeries(Source source, Map<?, ?> data) {
    return Series.builder()
        .sourceId(source.getId())
        .externalId(getIntValue(data, "series_id"))
        .num(getIntValue(data, "num"))
        .name(getStringValue(data, "name"))
        .categoryId(getIntValue(data, "category_id"))
        .isAdult(getBooleanValue(data, "is_adult"))
        .labels(labelExtractor.extractLabels(getStringValue(data, "name")))
        .data(convertMapToJson(data))
        .build();
  }

  private Integer getIntValue(Map<?, ?> data, String key) {
    Object value = data.get(key);
    if (value instanceof Integer) {
      return (Integer) value;
    } else if (value instanceof Number) {
      return ((Number) value).intValue();
    } else if (value instanceof String) {
      try {
        return Integer.parseInt((String) value);
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private String getStringValue(Map<?, ?> data, String key) {
    Object value = data.get(key);
    return value != null ? value.toString() : null;
  }

  private Boolean getBooleanValue(Map<?, ?> data, String key) {
    Object value = data.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    } else if (value instanceof String) {
      return Boolean.parseBoolean((String) value);
    } else if (value instanceof Number) {
      return ((Number) value).intValue() == 1;
    }
    return false;
  }

  private String convertMapToJson(Map<?, ?> data) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
    } catch (Exception e) {
      return "{}";
    }
  }
}
