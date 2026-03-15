package org.anasoid.iptvorganizer.controllers.xtream;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.models.enums.ConnectXtreamStreamMode;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;
import org.anasoid.iptvorganizer.services.xtream.ContentFilterService;
import org.anasoid.iptvorganizer.services.xtream.FilterContext;
import org.anasoid.iptvorganizer.utils.streaming.StreamModeHandler;

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
 * Date :   3/15/26
 */
@Slf4j
public abstract class AbstractDataController {

  @Inject
  @Getter(AccessLevel.PROTECTED)
  StreamModeHandler streamModeHandler;

  @Inject
  @Getter(AccessLevel.PROTECTED)
  ContentFilterService contentFilterService;

  @Inject
  @Getter(AccessLevel.PROTECTED)
  CategoryService categoryService;

  @Inject
  @Getter(AccessLevel.PROTECTED)
  LiveStreamService liveStreamService;

  @Inject
  @Getter(AccessLevel.PROTECTED)
  VodStreamService vodStreamService;

  @Inject
  @Getter(AccessLevel.PROTECTED)
  SeriesService seriesService;

  protected Response getStream(
      Client client,
      Source source,
      String streamUrl,
      ConnectXtreamStreamMode streamMode,
      UriInfo uriInfo,
      HttpHeaders httpHeaders) {

    return switch (streamMode) {
      case REDIRECT -> streamModeHandler.handleRedirectMode(streamUrl);
      case DIRECT -> streamModeHandler.handleDirectMode(client, source, streamUrl, httpHeaders);
      case PROXY -> streamModeHandler.handleProxyMode(uriInfo, client, streamUrl);
      case NO_PROXY -> streamModeHandler.handleDirectMode(client, source, streamUrl, httpHeaders);
      case DEFAULT -> streamModeHandler.handleProxyMode(uriInfo, client, streamUrl);
      default -> streamModeHandler.handleUnknownMode(streamMode.toString());
    };
  }

  /**
   * Check if client has access to specific stream
   *
   * @param client The authenticated client
   * @param source The source
   * @param streamId Stream ID to check
   * @param streamType Stream type (live, movie, series)
   * @return true if stream access is allowed
   */
  protected boolean hasStreamAccess(
      Client client, Source source, String streamId, StreamType streamType) {
    try {
      // Parse stream ID as integer
      int streamIdInt;
      try {
        streamIdInt = Integer.parseInt(streamId);
      } catch (NumberFormatException e) {
        log.warn("Invalid stream ID format: {}", streamId);
        return false;
      }

      // Load stream from database
      BaseStream stream = loadStream(source.getId(), streamIdInt, streamType);
      if (stream == null) {
        log.warn(
            "Stream not found - source: {}, type: {}, id: {}",
            source.getId(),
            streamType,
            streamId);
        return false; // Stream not found
      }

      // Load category
      Category category =
          categoryService.findBySourceAndCategoryId(
              source.getId(), stream.getCategoryId(), streamType.getCategoryType());

      // Build filtering context and check access
      FilterContext context = contentFilterService.buildFilterContext(client);
      return contentFilterService.shouldIncludeStream(context, stream, category);
    } catch (Exception e) {
      log.error("Error checking stream access", e);
      return false; // Deny access on error
    }
  }

  /**
   * Load stream from database by type
   *
   * @param sourceId Source ID
   * @param streamId Stream ID
   * @param streamType Stream type (live, movie, series)
   * @return Stream or null if not found
   */
  private BaseStream loadStream(Long sourceId, Integer streamId, StreamType streamType) {
    return switch (streamType) {
      case StreamType.LIVE -> liveStreamService.findBySourceAndStreamId(sourceId, streamId);
      case StreamType.VOD -> vodStreamService.findBySourceAndStreamId(sourceId, streamId);
      case StreamType.SERIES -> seriesService.findBySourceAndStreamId(sourceId, streamId);
      default -> null;
    };
  }
}
