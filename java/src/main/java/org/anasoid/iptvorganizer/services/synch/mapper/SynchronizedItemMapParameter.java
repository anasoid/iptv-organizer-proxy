package org.anasoid.iptvorganizer.services.synch.mapper;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.anasoid.iptvorganizer.models.entity.Source;

@SuperBuilder
@Getter
@Setter
public class SynchronizedItemMapParameter {

  private Source source;
  private Map data;
  private int num;
  private Integer unknownCategoryId;
}
