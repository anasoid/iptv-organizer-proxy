package org.anasoid.iptvorganizer.dto.xtream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Xtream Codes API authentication response wrapper */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class XtreamAuthResponse {
  @JsonProperty("user_info")
  private XtreamUserInfo userInfo;

  @JsonProperty("server_info")
  private XtreamServerInfo serverInfo;
}
