package org.anasoid.iptvorganizer.models.entity.stream;

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
public class LiveStream extends BaseStream implements StreamLike {
  @Override
  public StreamType getStreamType() {
    return StreamType.LIVE;
  }
}
