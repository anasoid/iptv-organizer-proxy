package org.anasoid.iptvorganizer.models;

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
public class Filter extends BaseEntity {
  private String name;
  private String description;
  private String filterConfig;
  private Boolean useSourceFilter;
  private String favoris;
}
