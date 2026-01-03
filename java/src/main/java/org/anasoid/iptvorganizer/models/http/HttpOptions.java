package org.anasoid.iptvorganizer.models.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HttpOptions {
    private String username;
    private String password;
    private Boolean followRedirects;
    private Integer timeout;
    private Map<String, String> headers;
    private Integer maxRetries;
}
