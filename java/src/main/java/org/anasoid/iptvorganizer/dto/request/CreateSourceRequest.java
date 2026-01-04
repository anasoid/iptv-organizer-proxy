package org.anasoid.iptvorganizer.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a source
 * Maps snake_case field names from frontend to camelCase Java properties
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSourceRequest {
    private String name;
    private String url;
    private String username;
    private String password;

    @JsonProperty("sync_interval")
    private Integer syncInterval;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("enableproxy")
    private Boolean enableProxy;

    @JsonProperty("disablestreamproxy")
    private Boolean disableStreamProxy;

    @JsonProperty("stream_follow_location")
    private Boolean streamFollowLocation;
}
