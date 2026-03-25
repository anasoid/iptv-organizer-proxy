package org.anasoid.iptvorganizer.models.http;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.anasoid.iptvorganizer.models.entity.Proxy;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProxyOptions {
  private Proxy proxy;
}
