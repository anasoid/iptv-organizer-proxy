package org.anasoid.iptvorganizer.models.history;

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

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;

/**
 * Represents a single stream-watch event recorded in the client's in-memory history.
 *
 * <p>Only identity ({@code streamId}, {@code streamType}) and timing ({@code start}, {@code end})
 * are stored. Display metadata (stream name, category name) is resolved on demand when the admin
 * API returns the list, keeping the in-memory footprint small.
 *
 * <ul>
 *   <li>{@code start} – time of the first (or sole) call to this stream session.
 *   <li>{@code end} – {@code null} until a subsequent call to the same stream happens within the
 *       configured update window, at which point it is set to the time of that follow-up call.
 * </ul>
 *
 * <p>Instances are intentionally mutable on {@code end} so the service can update it without
 * replacing the whole entry. All other fields are final.
 */
@Getter
@AllArgsConstructor
public class StreamHistoryEntry {

  /** External stream ID (as seen in the URL, e.g. "12345"). */
  private final String streamId;

  /** Type of the stream: LIVE, VOD, or SERIES. */
  private final StreamType streamType;

  /** Timestamp when this stream session started (first call). */
  private final LocalDateTime start;

  /**
   * Timestamp of the last observed call within the update window, or {@code null} if only one call
   * was recorded. Marked {@code volatile} so reads from other threads always see the latest value.
   */
  @Setter private volatile LocalDateTime end;
}
