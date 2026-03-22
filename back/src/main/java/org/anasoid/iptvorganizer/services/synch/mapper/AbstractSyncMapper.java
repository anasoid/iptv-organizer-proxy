package org.anasoid.iptvorganizer.services.synch.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import org.anasoid.iptvorganizer.helper.LabelExtractorHelper;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamLike;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.services.synch.BlackListFilterService;

@ApplicationScoped
public abstract class AbstractSyncMapper<T extends BaseStream & StreamLike> {

  @Inject LabelExtractorHelper labelExtractor;

  @Inject BlackListFilterService blackListFilterService;

  public Category mapToCategory(SynchronizedItemMapParameter param) {

    Category category = new Category();
    category.setSourceId(param.getSource().getId());
    category.setExternalId(getIntValue(param.getData(), "category_id"));
    category.setName(getStringValue(param.getData(), "category_name"));
    category.setType(getStreamType().getCategoryType());
    category.setNum(param.getNum());
    category.setParentId(getIntValue(param.getData(), "parent_id"));
    category.setLabels(labelExtractor.extractLabels(category.getName()));

    // Apply blacklist filter during import
    Category.BlackListStatus blackListStatus =
        blackListFilterService.applyBlackListFilter(category, param.getSource());
    category.setBlackList(blackListStatus);

    return category;
  }

  protected T mapToStream(T stream, SynchronizedItemMapParameter param) {

    stream.setSourceId(param.getSource().getId());
    stream.setNum(param.getNum());
    stream.setName(getStringValue(param.getData(), "name"));
    stream.setCategoryId(getIntValue(param.getData(), "category_id"));
    if (stream.getCategoryId() == null) {
      stream.setCategoryId(param.getUnknownCategoryId());
    }
    stream.setIsAdult(getBooleanValue(param.getData(), "is_adult"));
    stream.setLabels(labelExtractor.extractLabels(getStringValue(param.getData(), "name")));
    stream.setCategoryIds(getStringValue(param.getData(), "category_ids"));
    stream.setData(convertMapToJson(param.getData()));

    // Multi-key mapping for addedDate, releaseDate, rating, tmdb
    stream.setAddedDate(getFirstLocalDate(param.getData(), "addedDate", "added_date", "added"));
    stream.setReleaseDate(getFirstLocalDate(param.getData(), "releaseDate", "release_date"));
    stream.setRating(getFirstDouble(param.getData(), "rating"));
    stream.setTmdb(getFirstLong(param.getData(), "tmdb", "tmdb_id"));
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

  // Utility: get first non-null LocalDate from possible keys
  protected java.time.LocalDate getFirstLocalDate(Map<?, ?> data, String... keys) {
    for (String key : keys) {
      Object value = data.get(key);
      if (value instanceof java.time.LocalDate) {
        return (java.time.LocalDate) value;
      } else if (value instanceof String) {
        try {
          if (!((String) value).isEmpty()) {
            return java.time.LocalDate.parse((String) value);
          }
        } catch (Exception ignored) {
        }
      } else if (value instanceof Number) {
        // Support Unix timestamp (seconds or milliseconds)
        long ts = ((Number) value).longValue();
        // Heuristic: if ts > 10^12, treat as milliseconds, else seconds
        if (ts > 1000000000000L) {
          ts = ts / 1000L;
        }
        try {
          return java.time.Instant.ofEpochSecond(ts)
              .atZone(java.time.ZoneId.systemDefault())
              .toLocalDate();
        } catch (Exception ignored) {
        }
      }
    }
    return null;
  }

  // Utility: get first non-null Double from possible keys
  protected Double getFirstDouble(Map<?, ?> data, String... keys) {
    for (String key : keys) {
      Object value = data.get(key);
      if (value instanceof Number) {
        return ((Number) value).doubleValue();
      } else if (value instanceof String) {
        try {
          if (!((String) value).isEmpty()) {
            return Double.parseDouble((String) value);
          }
        } catch (Exception ignored) {
        }
      }
    }
    return null;
  }

  // Utility: get first non-null Long from possible keys
  protected Long getFirstLong(Map<?, ?> data, String... keys) {
    for (String key : keys) {
      Object value = data.get(key);
      if (value instanceof Number) {
        return ((Number) value).longValue();
      } else if (value instanceof String) {
        try {
          if (!((String) value).isEmpty()) {
            return Long.parseLong((String) value);
          }
        } catch (Exception ignored) {
        }
      }
    }
    return null;
  }

  abstract StreamType getStreamType();
}
