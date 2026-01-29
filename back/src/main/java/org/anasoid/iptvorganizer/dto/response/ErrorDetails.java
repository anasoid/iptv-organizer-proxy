package org.anasoid.iptvorganizer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Detailed error information included in error responses */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDetails {
  private String message;
  private String errorCode;
  private int status;
  private String exceptionType;
  private List<String> stackTrace;
  private Map<String, String> fieldErrors;
  private LocalDateTime timestamp;
  private String path;
}
