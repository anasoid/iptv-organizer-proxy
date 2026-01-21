package org.anasoid.iptvorganizer.dto.xtream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Xtream Codes API user information response */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class XtreamUserInfo {
  private String username;
  private String password;
  private String message;
  private Integer auth;
  private String status;

  @JsonProperty("exp_date")
  private Long expDate;

  @JsonProperty("is_trial")
  private String isTrial;

  @JsonProperty("active_cons")
  private String activeConnections;

  @JsonProperty("created_at")
  private Long createdAt;

  @JsonProperty("max_connections")
  private String maxConnections;

  @JsonProperty("allowed_output_formats")
  private List<String> allowedOutputFormats;
}
