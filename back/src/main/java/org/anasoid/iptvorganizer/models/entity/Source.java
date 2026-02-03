package org.anasoid.iptvorganizer.models.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.anasoid.iptvorganizer.models.enums.ConnectXmltvMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamApiMode;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamStreamMode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Source extends BaseEntity {
  private String name;
  private String url;
  private String username;
  private String password;
  private Integer syncInterval;
  private LocalDateTime lastSync;
  private LocalDateTime nextSync;
  private Boolean isActive;
  private Long proxyId;
  private ConnectXtreamApiMode connectXtreamApi;
  private ConnectXtreamStreamMode connectXtreamStream;
  private ConnectXmltvMode connectXmltv;
}
