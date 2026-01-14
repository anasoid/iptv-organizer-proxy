package org.anasoid.iptvorganizer.services.synch.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.anasoid.iptvorganizer.helper.LabelExtractorHelper;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamLike;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

@ApplicationScoped
public abstract class AbstractSyncMapper<T extends BaseStream & StreamLike> {

  @Inject LabelExtractorHelper labelExtractor;

  /** Map API response data to Category objects */
  public CategoryMappingResult mapCategoryData(Source source, List<Map> categoryMaps) {
    List<Category> categories = new ArrayList<>();
    Set<Integer> fetchedCategoryIds = new HashSet<>();
    AtomicInteger num = new AtomicInteger(1);

    for (Map catData : categoryMaps) {
      Category category = new Category();
      category.setSourceId(source.getId());
      category.setExternalId(getIntValue(catData, "category_id"));
      category.setName(getStringValue(catData, "category_name"));
      category.setType(getStreamType().getCategoryType());
      category.setNum(num.getAndIncrement());
      category.setParentId(getIntValue(catData, "parent_id"));
      category.setLabels(labelExtractor.extractLabels(category.getName()));

      categories.add(category);
      fetchedCategoryIds.add(category.getExternalId());
    }

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

  public Category mapToCategory(SynchronizedItemMapParameter param) {

    Category category = new Category();
    category.setSourceId(param.getSource().getId());
    category.setExternalId(getIntValue(param.getData(), "category_id"));
    category.setName(getStringValue(param.getData(), "category_name"));
    category.setType(getStreamType().getCategoryType());
    category.setNum(param.getNum());
    category.setParentId(getIntValue(param.getData(), "parent_id"));
    category.setLabels(labelExtractor.extractLabels(category.getName()));

    return category;
  }

  protected T mapToStream(T stream, SynchronizedItemMapParameter param) {

    stream.setSourceId(param.getSource().getId());
    stream.setNum(param.getNum());
    stream.setName(getStringValue(param.getData(), "name"));
    stream.setCategoryId(getIntValue(param.getData(), "category_id"));
    if (stream.getCategoryId() == null) {
      stream.setCategoryId(param.getUnknownCategoryId().await().indefinitely());
    }
    stream.setIsAdult(getBooleanValue(param.getData(), "is_adult"));
    stream.setLabels(labelExtractor.extractLabels(getStringValue(param.getData(), "name")));
    stream.setData(convertMapToJson(param.getData()));
    return stream;
  }

  /** Map API response to LiveStream entity */
  public abstract T mapToStream(SynchronizedItemMapParameter param);

  protected Integer getIntValue(Map<?, ?> data, String key) {
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

  protected String getStringValue(Map<?, ?> data, String key) {
    Object value = data.get(key);
    return value != null ? value.toString() : null;
  }

  protected Boolean getBooleanValue(Map<?, ?> data, String key) {
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

  protected String convertMapToJson(Map<?, ?> data) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(data);
    } catch (Exception e) {
      return "{}";
    }
  }

  abstract StreamType getStreamType();
}
