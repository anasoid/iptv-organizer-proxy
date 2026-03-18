package org.anasoid.iptvorganizer.dto.xtream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Xtream Codes API server information response */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class XtreamServerInfo {
  private String url;
  private String port;

  @JsonProperty("https_port")
  private String httpsPort;

  @JsonProperty("server_protocol")
  private String serverProtocol;

  @JsonProperty("rtmp_port")
  private String rtmpPort;

  @JsonProperty("timestamp_now")
  private Long timestampNow;

  @JsonProperty("time_now")
  private String timeNow;
}
