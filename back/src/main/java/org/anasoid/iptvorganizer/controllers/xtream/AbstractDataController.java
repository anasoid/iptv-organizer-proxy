package org.anasoid.iptvorganizer.controllers.xtream;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.HttpRequestDto;
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
      HttpRequestDto request,
      ConnectXtreamStreamMode streamMode,
      UriInfo uriInfo) {

    return switch (streamMode) {
      case REDIRECT -> streamModeHandler.handleRedirectMode(request.getUrl());
      case DIRECT -> streamModeHandler.handleDirectMode(request, client, source);
      case PROXY -> streamModeHandler.handleProxyMode(uriInfo, client, request.getUrl());
      case NO_PROXY -> streamModeHandler.handleDirectMode(request, client, source);
      case DEFAULT -> streamModeHandler.handleDirectMode(request, client, source);
      default -> streamModeHandler.handleUnknownMode(streamMode.toString());
    };
  }

  /**
   * Loads the stream and checks whether the client is allowed to access it.
   *
   * <p>Returns an {@link Optional} containing the stream if access is granted, or {@link
   * Optional#empty()} if the stream was not found, the client is not allowed, or any error occurs.
   * Callers can use the returned stream to extract metadata (e.g. name) without a second DB query.
   *
   * @param client The authenticated client
   * @param source The source
   * @param streamId Stream ID to check (as a URL path segment string)
   * @param streamType Stream type (live, movie, series)
   * @return {@code Optional.of(stream)} if allowed; {@code Optional.empty()} otherwise
   */
  protected Optional<BaseStream> loadAccessibleStream(
      Client client, Source source, String streamId, StreamType streamType) {
    try {
      int streamIdInt;
      try {
        streamIdInt = Integer.parseInt(streamId);
      } catch (NumberFormatException e) {
        log.warn("Invalid stream ID format: {}", streamId);
        return Optional.empty();
      }

      BaseStream stream = loadStream(source.getId(), streamIdInt, streamType);
      if (stream == null) {
        log.warn(
            "Stream not found - source: {}, type: {}, id: {}",
            source.getId(),
            streamType,
            streamId);
        return Optional.empty();
      }

      Category category =
          categoryService.findBySourceAndCategoryId(
              source.getId(), stream.getCategoryId(), streamType.getCategoryType());

      FilterContext context = contentFilterService.buildFilterContext(client);
      if (contentFilterService.shouldIncludeStream(context, stream, category)) {
        return Optional.of(stream);
      }
      return Optional.empty();
    } catch (Exception e) {
      log.error("Error checking stream access", e);
      return Optional.empty();
    }
  }

  /**
   * Check if client has access to specific stream.
   *
   * @param client The authenticated client
   * @param source The source
   * @param streamId Stream ID to check
   * @param streamType Stream type (live, movie, series)
   * @return true if stream access is allowed
   */
  protected boolean hasStreamAccess(
      Client client, Source source, String streamId, StreamType streamType) {
    return loadAccessibleStream(client, source, streamId, streamType).isPresent();
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
