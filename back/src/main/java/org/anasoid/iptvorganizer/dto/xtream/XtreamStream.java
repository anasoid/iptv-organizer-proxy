package org.anasoid.iptvorganizer.dto.xtream;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic Xtream Codes API stream object
 *
 * <p>Uses @JsonAnySetter to capture all fields dynamically, as Xtream API returns varying fields
 * depending on stream type. For raw passthrough without transformation, fields are stored as-is.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class XtreamStream {
  @Builder.Default private Map<String, Object> fields = new HashMap<>();

  @JsonAnySetter
  public void setField(String name, Object value) {
    fields.put(name, value);
  }

  public Object getField(String name) {
    return fields.get(name);
  }

  public String getStringField(String name) {
    Object value = fields.get(name);
    return value != null ? value.toString() : null;
  }

  public Integer getIntField(String name) {
    Object value = fields.get(name);
    if (value == null) return null;
    if (value instanceof Number) return ((Number) value).intValue();
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public Long getLongField(String name) {
    Object value = fields.get(name);
    if (value == null) return null;
    if (value instanceof Number) return ((Number) value).longValue();
    try {
      return Long.parseLong(value.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
