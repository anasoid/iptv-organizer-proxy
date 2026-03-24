package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.history.StreamHistoryEntryDto;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.models.history.StreamHistoryEntry;
import org.anasoid.iptvorganizer.services.ClientService;
import org.anasoid.iptvorganizer.services.history.ClientHistoryService;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/**
 * Admin REST controller exposing the in-memory stream-watch history for a given client.
 *
 * <p>All endpoints require an authenticated admin bearer token.
 *
 * <ul>
 *   <li>{@code GET /api/clients/{clientId}/history} – history list, most-recent-first, enriched
 *       with stream name and category name resolved at query time.
 *   <li>{@code DELETE /api/clients/{clientId}/history} – clears the history for the client.
 * </ul>
 *
 * <p><strong>Note:</strong> history is in-memory only and is lost on server restart.
 */
@Path("/api/clients/{clientId}/history")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Slf4j
public class ClientHistoryController extends BaseController {

  @Inject ClientHistoryService clientHistoryService;
  @Inject ClientService clientService;
  @Inject LiveStreamService liveStreamService;
  @Inject VodStreamService vodStreamService;
  @Inject SeriesService seriesService;
  @Inject CategoryService categoryService;

  /**
   * Returns the watch history for the specified client, ordered most-recent-first.
   *
   * <p>Each entry is enriched with {@code streamName} and {@code categoryName} resolved from the
   * source catalogue at query time. Unique {@code (streamId, streamType)} pairs are looked up only
   * once per request to avoid redundant database queries.
   *
   * @param clientId ID of the client
   * @return 200 OK with envelope {@code {data: [...], total: N}}
   */
  @GET
  public Response getHistory(@PathParam("clientId") Long clientId) {
    List<StreamHistoryEntry> entries = clientHistoryService.getHistory(clientId);

    // Resolve the client's sourceId – needed for DB lookups
    Long sourceId = resolveSourceId(clientId);

    List<StreamHistoryEntryDto> enriched = enrich(entries, sourceId);
    return ResponseUtils.ok(Map.of("data", enriched, "total", enriched.size()));
  }

  /**
   * Clears all watch-history entries for the specified client.
   *
   * @param clientId ID of the client
   * @return 204 No Content
   */
  @DELETE
  public Response clearHistory(@PathParam("clientId") Long clientId) {
    clientHistoryService.clearHistory(clientId);
    return Response.noContent().build();
  }

  // -------------------------------------------------------------------------
  // Enrichment helpers
  // -------------------------------------------------------------------------

  /**
   * Resolves the source ID for a client.
   *
   * @return sourceId, or {@code null} if the client is not found
   */
  private Long resolveSourceId(Long clientId) {
    try {
      Client client = clientService.getById(clientId);
      return client != null ? client.getSourceId() : null;
    } catch (Exception e) {
      log.warn("Could not resolve sourceId for client={}: {}", clientId, e.getMessage());
      return null;
    }
  }

  /**
   * Enriches a list of raw {@link StreamHistoryEntry} with stream name and category name.
   *
   * <p>Deduplicates lookups: each unique {@code (streamId, streamType)} pair is fetched from the
   * database exactly once per request, regardless of how many history entries reference it.
   */
  private List<StreamHistoryEntryDto> enrich(List<StreamHistoryEntry> entries, Long sourceId) {
    if (entries.isEmpty()) {
      return List.of();
    }

    // Build a cache: "streamId:streamType" -> resolved stream (null if not found)
    Map<String, BaseStream> streamCache = new HashMap<>();
    // Build a cache: "categoryId:categoryType" -> category name (null if not found)
    Map<String, String> categoryNameCache = new HashMap<>();

    return entries.stream()
        .map(e -> toDto(e, sourceId, streamCache, categoryNameCache))
        .collect(Collectors.toList());
  }

  private StreamHistoryEntryDto toDto(
      StreamHistoryEntry entry,
      Long sourceId,
      Map<String, BaseStream> streamCache,
      Map<String, String> categoryNameCache) {

    String streamName = null;
    String categoryName = null;

    if (sourceId != null) {
      String streamKey = entry.getStreamId() + ":" + entry.getStreamType();
      BaseStream stream = streamCache.computeIfAbsent(streamKey, k -> loadStream(sourceId, entry));

      if (stream != null) {
        streamName = stream.getName();
        if (stream.getCategoryId() != null) {
          String catKey = stream.getCategoryId() + ":" + entry.getStreamType().getCategoryType();
          categoryName =
              categoryNameCache.computeIfAbsent(
                  catKey, k -> loadCategoryName(sourceId, stream, entry.getStreamType()));
        }
      }
    }

    return StreamHistoryEntryDto.builder()
        .streamId(entry.getStreamId())
        .streamType(entry.getStreamType())
        .streamName(streamName)
        .categoryName(categoryName)
        .start(entry.getStart())
        .end(entry.getEnd())
        .build();
  }

  /** Loads a stream from the appropriate service; returns {@code null} on any failure. */
  private BaseStream loadStream(Long sourceId, StreamHistoryEntry entry) {
    try {
      int id = Integer.parseInt(entry.getStreamId());
      return switch (entry.getStreamType()) {
        case LIVE -> liveStreamService.findBySourceAndStreamId(sourceId, id);
        case VOD -> vodStreamService.findBySourceAndStreamId(sourceId, id);
        case SERIES -> seriesService.findBySourceAndStreamId(sourceId, id);
        default -> null;
      };
    } catch (NumberFormatException e) {
      log.warn("Non-integer streamId in history: {}", entry.getStreamId());
      return null;
    } catch (Exception e) {
      log.warn(
          "Error loading stream {} for history enrichment: {}",
          entry.getStreamId(),
          e.getMessage());
      return null;
    }
  }

  /** Looks up a category name; returns {@code null} on any failure. */
  private String loadCategoryName(Long sourceId, BaseStream stream, StreamType streamType) {
    try {
      Category category =
          categoryService.findBySourceAndCategoryId(
              sourceId, stream.getCategoryId(), streamType.getCategoryType());
      return category != null ? category.getName() : null;
    } catch (Exception e) {
      log.warn(
          "Error loading category {} for history enrichment: {}",
          stream.getCategoryId(),
          e.getMessage());
      return null;
    }
  }
}
