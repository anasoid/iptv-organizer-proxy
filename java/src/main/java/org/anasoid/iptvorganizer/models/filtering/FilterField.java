package org.anasoid.iptvorganizer.models.filtering;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FilterField {
  NAME("name"),
  CATEGORY_NAME("categoryName"),
  IS_ADULT("isAdult"),
  LABELS("labels");

  private final String fieldName;

  FilterField(String fieldName) {
    this.fieldName = fieldName;
  }

  @JsonValue
  public String getFieldName() {
    return fieldName;
  }

  @JsonCreator
  public static FilterField fromFieldName(String fieldName) {
    for (FilterField field : FilterField.values()) {
      if (field.fieldName.equals(fieldName)) {
        return field;
      }
    }
    throw new IllegalArgumentException("Unknown field: " + fieldName);
  }
}
