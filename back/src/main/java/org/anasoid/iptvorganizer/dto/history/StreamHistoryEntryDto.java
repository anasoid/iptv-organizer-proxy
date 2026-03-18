package org.anasoid.iptvorganizer.dto.history;

/*
 * Copyright 2023-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * @author : anasoid
 * Date :   3/17/26
 */

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

/**
 * Read-only DTO returned by the admin history endpoint.
 *
 * <p>Extends the raw {@link org.anasoid.iptvorganizer.models.history.StreamHistoryEntry} with
 * {@code streamName} and {@code categoryName} resolved at query time from the source catalogue.
 * Both fields are {@code null} when the stream or category could not be found (e.g. content was
 * removed from the source since the access was recorded).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamHistoryEntryDto {

  /** External stream ID as stored in the history (matches the URL path segment). */
  String streamId;

  /** LIVE, VOD, or SERIES. */
  StreamType streamType;

  /** Human-readable stream name resolved from the source catalogue; {@code null} if not found. */
  String streamName;

  /** Name of the stream's category; {@code null} if not found. */
  String categoryName;

  /** Timestamp when this session was first observed. */
  LocalDateTime start;

  /**
   * Timestamp of the last call within the update window, or {@code null} if only one call was
   * recorded for this session.
   */
  LocalDateTime end;
}
