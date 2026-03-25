package org.anasoid.iptvorganizer.models.entity.stream;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class VodStream extends BaseStream implements StreamLike {
  @Override
  public StreamType getStreamType() {
    return StreamType.VOD;
  }
}
