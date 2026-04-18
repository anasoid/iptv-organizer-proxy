package org.anasoid.iptvorganizer.models.entity.stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Category extends SourcedEntity {

  private String name;
  private String type;
  private Integer parentId;
  private String labels;
  private BlackListStatus blackList;

  public enum BlackListStatus {
    DEFAULT("default", false),
    HIDE("hide", true),
    VISIBLE("visible", false),
    FORCE_HIDE("force_hide", true),
    FORCE_VISIBLE("force_visible", false);

    private final String value;
    private final boolean hide;

    BlackListStatus(String value, boolean hide) {
      this.value = value;
      this.hide = hide;
    }

    @JsonValue
    public String getValue() {
      return value;
    }

    public boolean isHide() {
      return hide;
    }

    public static BlackListStatus fromValue(String value) {
      if (value == null) {
        return DEFAULT;
      }
      for (BlackListStatus status : BlackListStatus.values()) {
        if (status.value.equalsIgnoreCase(value)) {
          return status;
        }
      }
      throw new IllegalArgumentException("Unknown blacklist status: " + value);
    }
  }
}
