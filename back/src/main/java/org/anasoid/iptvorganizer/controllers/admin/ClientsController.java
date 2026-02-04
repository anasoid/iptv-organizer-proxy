package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.exceptions.NotFoundException;
import org.anasoid.iptvorganizer.exceptions.ValidationException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.models.entity.stream.Series;
import org.anasoid.iptvorganizer.models.entity.stream.VodStream;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.services.ClientService;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;
import org.anasoid.iptvorganizer.services.xtream.XtreamUserService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;

/** Clients controller CRUD operations for clients with search and filtering */
@Path("/api/clients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class ClientsController extends BaseController {

  @Inject ClientService clientService;
  @Inject XtreamUserService xtreamUserService;
  @Inject SourceRepository sourceRepository;
  @Inject CategoryService categoryService;
  @Inject LiveStreamService liveStreamService;
  @Inject VodStreamService vodStreamService;
  @Inject SeriesService seriesService;

  /** Get all clients with pagination and search GET /api/clients?page=1&limit=20&search= */
  @GET
  public Response getAllClients(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit,
      @QueryParam("search") String search) {

    if (page < 1 || limit < 1) {
      throw new ValidationException("Page and limit must be greater than 0");
    }

    if (search != null && !search.isBlank()) {
      var clients = clientService.searchClients(search, page, limit);
      long total = clientService.countSearchClients(search);
      var pagination = PaginationMeta.of(page, limit, total);
      return ResponseUtils.okWithPagination(clients, pagination);
    } else {
      var clients = clientService.getAllPaged(page, limit);
      long total = clientService.count();
      var pagination = PaginationMeta.of(page, limit, total);
      return ResponseUtils.okWithPagination(clients, pagination);
    }
  }

  /** Get client by ID GET /api/clients/:id */
  @GET
  @Path("/{id}")
  public Response getClient(@PathParam("id") Long id) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }
    return ResponseUtils.ok(client);
  }

  /** Create client POST /api/clients */
  @POST
  public Response createClient(Client request) {
    if (request.getUsername() == null || request.getUsername().isBlank()) {
      throw new ValidationException("Username is required");
    }

    // Set defaults for new client
    if (request.getIsActive() == null) {
      request.setIsActive(true);
    }
    if (request.getHideAdultContent() == null) {
      request.setHideAdultContent(false);
    }

    Client savedClient = clientService.save(request);
    return ResponseUtils.created(savedClient);
  }

  /** Update client PUT /api/clients/:id */
  @PUT
  @Path("/{id}")
  public Response updateClient(@PathParam("id") Long id, Client request) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    // Merge non-null fields from request into existing client
    if (request.getSourceId() != null) client.setSourceId(request.getSourceId());
    if (request.getFilterId() != null) client.setFilterId(request.getFilterId());
    if (request.getUsername() != null) client.setUsername(request.getUsername());
    if (request.getPassword() != null && !request.getPassword().isBlank())
      client.setPassword(request.getPassword());
    if (request.getName() != null) client.setName(request.getName());
    if (request.getEmail() != null) client.setEmail(request.getEmail());
    if (request.getExpiryDate() != null) client.setExpiryDate(request.getExpiryDate());
    if (request.getIsActive() != null) client.setIsActive(request.getIsActive());
    if (request.getHideAdultContent() != null)
      client.setHideAdultContent(request.getHideAdultContent());
    if (request.getEnableProxy() != null) client.setEnableProxy(request.getEnableProxy());
    if (request.getEnableTunnel() != null) client.setEnableTunnel(request.getEnableTunnel());
    if (request.getConnectXtreamApi() != null)
      client.setConnectXtreamApi(request.getConnectXtreamApi());
    if (request.getConnectXtreamStream() != null)
      client.setConnectXtreamStream(request.getConnectXtreamStream());
    if (request.getConnectXmltv() != null) client.setConnectXmltv(request.getConnectXmltv());
    if (request.getNotes() != null) client.setNotes(request.getNotes());

    clientService.update(client);
    return ResponseUtils.ok(client);
  }

  /** Delete client DELETE /api/clients/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteClient(@PathParam("id") Long id) {
    clientService.delete(id);
    return ResponseUtils.okMessage("Client deleted successfully");
  }

  /** Get client logs GET /api/clients/:id/logs?limit=20 */
  @GET
  @Path("/{id}/logs")
  public Response getClientLogs(
      @PathParam("id") Long id, @QueryParam("limit") @DefaultValue("20") int limit) {
    // TODO: Implement client logs retrieval
    return ResponseUtils.okMessage("Client logs retrieved");
  }

  /** Collect JsonStreamResult iterator to list and close the stream */
  private List<Map<?, ?>> collectToList(JsonStreamResult<Map<?, ?>> result) {
    List<Map<?, ?>> list = new ArrayList<>();
    Iterator<Map<?, ?>> iterator = result.iterator();
    while (iterator.hasNext()) {
      list.add(iterator.next());
    }
    try {
      result.close();
    } catch (Exception e) {
      // Ignore close errors
    }
    return list;
  }

  // ==================== ALLOWED EXPORT ENDPOINTS ====================

  /** Export live categories for a client GET /api/clients/:id/export/live-categories */
  @GET
  @Path("/{id}/export/live-categories")
  public Response exportLiveCategories(@PathParam("id") Long id) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    JsonStreamResult<Map<?, ?>> result = xtreamUserService.getLiveCategories(client, source);
    List<Map<?, ?>> categories = collectToList(result);

    return ResponseUtils.ok(categories);
  }

  /** Export VOD categories for a client GET /api/clients/:id/export/vod-categories */
  @GET
  @Path("/{id}/export/vod-categories")
  public Response exportVodCategories(@PathParam("id") Long id) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    JsonStreamResult<Map<?, ?>> result = xtreamUserService.getVodCategories(client, source);
    List<Map<?, ?>> categories = collectToList(result);

    return ResponseUtils.ok(categories);
  }

  /** Export series categories for a client GET /api/clients/:id/export/series-categories */
  @GET
  @Path("/{id}/export/series-categories")
  public Response exportSeriesCategories(@PathParam("id") Long id) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    JsonStreamResult<Map<?, ?>> result = xtreamUserService.getSeriesCategories(client, source);
    List<Map<?, ?>> categories = collectToList(result);

    return ResponseUtils.ok(categories);
  }

  /** Export live streams for a client GET /api/clients/:id/export/live-streams?category_id=1 */
  @GET
  @Path("/{id}/export/live-streams")
  public Response exportLiveStreams(
      @PathParam("id") Long id, @QueryParam("category_id") Long categoryId) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    JsonStreamResult<Map<?, ?>> result =
        xtreamUserService.getLiveStreams(client, source, categoryId);
    List<Map<?, ?>> streams = collectToList(result);

    return ResponseUtils.ok(streams);
  }

  /** Export VOD streams for a client GET /api/clients/:id/export/vod-streams?category_id=1 */
  @GET
  @Path("/{id}/export/vod-streams")
  public Response exportVodStreams(
      @PathParam("id") Long id, @QueryParam("category_id") Long categoryId) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    JsonStreamResult<Map<?, ?>> result =
        xtreamUserService.getVodStreams(client, source, categoryId);
    List<Map<?, ?>> streams = collectToList(result);

    return ResponseUtils.ok(streams);
  }

  /** Export series for a client GET /api/clients/:id/export/series?category_id=1 */
  @GET
  @Path("/{id}/export/series")
  public Response exportSeries(
      @PathParam("id") Long id, @QueryParam("category_id") Long categoryId) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    JsonStreamResult<Map<?, ?>> result = xtreamUserService.getSeries(client, source, categoryId);
    List<Map<?, ?>> series = collectToList(result);

    return ResponseUtils.ok(series);
  }

  // ==================== BLOCKED EXPORT ENDPOINTS ====================

  /**
   * Export blocked live categories for a client GET /api/clients/:id/export/blocked-live-categories
   */
  @GET
  @Path("/{id}/export/blocked-live-categories")
  public Response exportBlockedLiveCategories(@PathParam("id") Long id) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    if (client.getFilterId() == null) {
      throw new ValidationException("Client has no filter assigned - cannot compute blocked items");
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    // Get ALL categories (unfiltered)
    List<Category> allCategories = categoryService.findBySourceAndType(source.getId(), "live");
    Set<String> allIds =
        allCategories.stream()
            .map(cat -> cat.getExternalId().toString())
            .collect(Collectors.toSet());

    // Get ALLOWED categories (filtered)
    JsonStreamResult<Map<?, ?>> allowedResult = xtreamUserService.getLiveCategories(client, source);
    Set<String> allowedIds =
        collectToList(allowedResult).stream()
            .map(m -> String.valueOf(m.get("category_id")))
            .collect(Collectors.toSet());

    // Compute BLOCKED = ALL - ALLOWED
    List<Map<?, ?>> blocked =
        allCategories.stream()
            .filter(cat -> !allowedIds.contains(cat.getExternalId().toString()))
            .map(
                cat -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("category_id", cat.getExternalId().toString());
                  map.put("category_name", cat.getName());
                  map.put("parent_id", cat.getParentId() != null ? cat.getParentId() : 0);
                  return map;
                })
            .collect(Collectors.toList());

    return ResponseUtils.ok(blocked);
  }

  /**
   * Export blocked VOD categories for a client GET /api/clients/:id/export/blocked-vod-categories
   */
  @GET
  @Path("/{id}/export/blocked-vod-categories")
  public Response exportBlockedVodCategories(@PathParam("id") Long id) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    if (client.getFilterId() == null) {
      throw new ValidationException("Client has no filter assigned - cannot compute blocked items");
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    // Get ALL categories (unfiltered)
    List<Category> allCategories = categoryService.findBySourceAndType(source.getId(), "vod");
    Set<String> allIds =
        allCategories.stream()
            .map(cat -> cat.getExternalId().toString())
            .collect(Collectors.toSet());

    // Get ALLOWED categories (filtered)
    JsonStreamResult<Map<?, ?>> allowedResult = xtreamUserService.getVodCategories(client, source);
    Set<String> allowedIds =
        collectToList(allowedResult).stream()
            .map(m -> String.valueOf(m.get("category_id")))
            .collect(Collectors.toSet());

    // Compute BLOCKED = ALL - ALLOWED
    List<Map<?, ?>> blocked =
        allCategories.stream()
            .filter(cat -> !allowedIds.contains(cat.getExternalId().toString()))
            .map(
                cat -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("category_id", cat.getExternalId().toString());
                  map.put("category_name", cat.getName());
                  map.put("parent_id", cat.getParentId() != null ? cat.getParentId() : 0);
                  return map;
                })
            .collect(Collectors.toList());

    return ResponseUtils.ok(blocked);
  }

  /**
   * Export blocked series categories for a client GET
   * /api/clients/:id/export/blocked-series-categories
   */
  @GET
  @Path("/{id}/export/blocked-series-categories")
  public Response exportBlockedSeriesCategories(@PathParam("id") Long id) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    if (client.getFilterId() == null) {
      throw new ValidationException("Client has no filter assigned - cannot compute blocked items");
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    // Get ALL categories (unfiltered)
    List<Category> allCategories = categoryService.findBySourceAndType(source.getId(), "series");
    Set<String> allIds =
        allCategories.stream()
            .map(cat -> cat.getExternalId().toString())
            .collect(Collectors.toSet());

    // Get ALLOWED categories (filtered)
    JsonStreamResult<Map<?, ?>> allowedResult =
        xtreamUserService.getSeriesCategories(client, source);
    Set<String> allowedIds =
        collectToList(allowedResult).stream()
            .map(m -> String.valueOf(m.get("category_id")))
            .collect(Collectors.toSet());

    // Compute BLOCKED = ALL - ALLOWED
    List<Map<?, ?>> blocked =
        allCategories.stream()
            .filter(cat -> !allowedIds.contains(cat.getExternalId().toString()))
            .map(
                cat -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("category_id", cat.getExternalId().toString());
                  map.put("category_name", cat.getName());
                  map.put("parent_id", cat.getParentId() != null ? cat.getParentId() : 0);
                  return map;
                })
            .collect(Collectors.toList());

    return ResponseUtils.ok(blocked);
  }

  /** Export blocked live streams for a client GET /api/clients/:id/export/blocked-live-streams */
  @GET
  @Path("/{id}/export/blocked-live-streams")
  public Response exportBlockedLiveStreams(@PathParam("id") Long id) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    if (client.getFilterId() == null) {
      throw new ValidationException("Client has no filter assigned - cannot compute blocked items");
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    // Get ALL streams
    List<LiveStream> allStreams = liveStreamService.findBySourceId(source.getId());

    // Get ALLOWED streams
    JsonStreamResult<Map<?, ?>> allowedResult =
        xtreamUserService.getLiveStreams(client, source, null);
    Set<String> allowedIds =
        collectToList(allowedResult).stream()
            .map(m -> String.valueOf(m.get("stream_id")))
            .collect(Collectors.toSet());

    // Compute BLOCKED
    List<Map<?, ?>> blocked =
        allStreams.stream()
            .filter(stream -> !allowedIds.contains(stream.getExternalId().toString()))
            .map(
                stream -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("stream_id", stream.getExternalId());
                  map.put("num", stream.getNum());
                  map.put("name", stream.getName());
                  map.put("category_id", stream.getCategoryId());
                  return map;
                })
            .collect(Collectors.toList());

    return ResponseUtils.ok(blocked);
  }

  /** Export blocked VOD streams for a client GET /api/clients/:id/export/blocked-vod-streams */
  @GET
  @Path("/{id}/export/blocked-vod-streams")
  public Response exportBlockedVodStreams(@PathParam("id") Long id) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    if (client.getFilterId() == null) {
      throw new ValidationException("Client has no filter assigned - cannot compute blocked items");
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    // Get ALL streams
    List<VodStream> allStreams = vodStreamService.findBySourceId(source.getId());

    // Get ALLOWED streams
    JsonStreamResult<Map<?, ?>> allowedResult =
        xtreamUserService.getVodStreams(client, source, null);
    Set<String> allowedIds =
        collectToList(allowedResult).stream()
            .map(m -> String.valueOf(m.get("stream_id")))
            .collect(Collectors.toSet());

    // Compute BLOCKED
    List<Map<?, ?>> blocked =
        allStreams.stream()
            .filter(stream -> !allowedIds.contains(stream.getExternalId().toString()))
            .map(
                stream -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("stream_id", stream.getExternalId());
                  map.put("num", stream.getNum());
                  map.put("name", stream.getName());
                  map.put("category_id", stream.getCategoryId());
                  return map;
                })
            .collect(Collectors.toList());

    return ResponseUtils.ok(blocked);
  }

  /** Export blocked series for a client GET /api/clients/:id/export/blocked-series */
  @GET
  @Path("/{id}/export/blocked-series")
  public Response exportBlockedSeries(@PathParam("id") Long id) {
    Client client = clientService.getById(id);
    if (client == null) {
      throw new NotFoundException("Client not found with ID: " + id);
    }

    if (client.getFilterId() == null) {
      throw new ValidationException("Client has no filter assigned - cannot compute blocked items");
    }

    Source source = sourceRepository.findById(client.getSourceId());
    if (source == null) {
      throw new ValidationException("Source not found for client");
    }

    // Get ALL series
    List<Series> allSeries = seriesService.findBySourceId(source.getId());

    // Get ALLOWED series
    JsonStreamResult<Map<?, ?>> allowedResult = xtreamUserService.getSeries(client, source, null);
    Set<String> allowedIds =
        collectToList(allowedResult).stream()
            .map(m -> String.valueOf(m.get("series_id")))
            .collect(Collectors.toSet());

    // Compute BLOCKED
    List<Map<?, ?>> blocked =
        allSeries.stream()
            .filter(series -> !allowedIds.contains(series.getExternalId().toString()))
            .map(
                series -> {
                  Map<String, Object> map = new HashMap<>();
                  map.put("series_id", series.getExternalId());
                  map.put("name", series.getName());
                  map.put("category_id", series.getCategoryId());
                  return map;
                })
            .collect(Collectors.toList());

    return ResponseUtils.ok(blocked);
  }
}
