package org.anasoid.iptvorganizer.models.filtering;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterRule {
  private FilterField field;
  private String pattern;
  private Object value;
  private FilterAction action;
}
