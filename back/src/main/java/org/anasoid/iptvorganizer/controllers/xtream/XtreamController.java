package org.anasoid.iptvorganizer.controllers.xtream;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.repositories.ClientRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.services.xtream.XtreamUserService;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;

/**
 * Xtream Codes API Controller
 *
 * <p>Public endpoint for Xtream API clients (VLC, Kodi, TiviMate, GSE IPTV, etc.) No JWT
 * authentication required - uses Xtream username/password credentials.
 *
 * <p>Endpoints: - /player_api.php (GET/POST) - Authentication and server info -
 * /player_api.php?action=get_live_categories - List live categories -
 * /player_api.php?action=get_vod_categories - List VOD categories -
 * /player_api.php?action=get_series_categories - List series categories -
 * /player_api.php?action=get_live_streams - List live streams -
 * /player_api.php?action=get_vod_streams - List VOD streams - /player_api.php?action=get_series -
 * List series
 */
@Slf4j
@Path("/player_api.php")
@ApplicationScoped
public class XtreamController {

  @Inject XtreamUserService xtreamUserService;
  @Inject ClientRepository clientRepository;
  @Inject SourceRepository sourceRepository;
  @Inject ObjectMapper objectMapper;

  /**
   * Authenticate client and return server/user info
   *
   * <p>Endpoint: /player_api.php (no action parameter) GET/POST
   *
   * @param username Client username
   * @param password Client password
   * @param action Optional action parameter (empty means authenticate)
   * @param uriInfo Request URI info for proxy URL construction
   * @return JSON response with user_info and server_info
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response handleRequest(
      @QueryParam("username") String username,
      @QueryParam("password") String password,
      @QueryParam("action") String action,
      @QueryParam("category_id") Long categoryId,
      @Context UriInfo uriInfo) {

    // If no action, treat as authenticate
    if (action == null || action.isEmpty()) {
      return authenticate(username, password, uriInfo);
    }

    // Validate and authenticate client
    var authResult = xtreamUserService.authenticateAndValidateClient(username, password);
    Client client = authResult.getClient();
    Source source = authResult.getSource();

    // Route to appropriate handler
    return handleAction(action, client, source, categoryId);
  }

  /**
   * Authenticate client and return server/user info
   *
   * @param username Client username
   * @param password Client password
   * @param uriInfo Request URI info
   * @return JSON response with authentication data
   */
  private Response authenticate(String username, String password, UriInfo uriInfo) {
    // Build proxy URL from request
    String proxyUrl = buildProxyUrl(uriInfo);

    // Authenticate and get response
    var authResponse = xtreamUserService.authenticate(username, password, proxyUrl);

    return Response.ok(authResponse).header("Content-Type", MediaType.APPLICATION_JSON).build();
  }

  /**
   * Route to appropriate action handler
   *
   * @param action The Xtream API action
   * @param client The authenticated client
   * @param source The source
   * @param categoryId Optional category filter
   * @return Response
   */
  private Response handleAction(String action, Client client, Source source, Long categoryId) {
    switch (action) {
      case "get_live_categories":
        return streamJsonArray(xtreamUserService.getLiveCategories(client, source));

      case "get_vod_categories":
        return streamJsonArray(xtreamUserService.getVodCategories(client, source));

      case "get_series_categories":
        return streamJsonArray(xtreamUserService.getSeriesCategories(client, source));

      case "get_live_streams":
        return streamJsonArray(xtreamUserService.getLiveStreams(client, source, categoryId));

      case "get_vod_streams":
        return streamJsonArray(xtreamUserService.getVodStreams(client, source, categoryId));

      case "get_series":
        return streamJsonArray(xtreamUserService.getSeries(client, source, categoryId));

      default:
        log.warn("Unknown Xtream API action: {}", action);
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Unknown action\"}")
            .header("Content-Type", MediaType.APPLICATION_JSON)
            .build();
    }
  }

  /**
   * Stream JSON array response with lazy Iterator
   *
   * @param jsonStreamResult The stream result with lazy Iterator
   * @return Response with streaming output
   */
  private Response streamJsonArray(JsonStreamResult<Map<?, ?>> jsonStreamResult) {
    return Response.ok(
            (StreamingOutput)
                os -> {
                  int itemCount = 0;
                  try {
                    os.write('[');
                    Iterator<Map<?, ?>> iterator = jsonStreamResult.iterator();
                    boolean isFirst = true;

                    while (iterator.hasNext()) {
                      if (!isFirst) {
                        os.write(',');
                      }
                      Map<?, ?> item = iterator.next();
                      // writeValueAsBytes instead of writeValue to avoid closing the stream
                      byte[] jsonBytes = objectMapper.writeValueAsBytes(item);
                      os.write(jsonBytes);
                      itemCount++;
                      isFirst = false;
                    }

                    log.debug("Completed streaming JSON array. Total items: {}", itemCount);
                    os.write(']');
                    os.flush();
                  } catch (IOException ex) {
                    // Log but don't throw - response is already being sent
                    if (ex.getMessage() != null && !ex.getMessage().contains("Stream is closed")) {
                      log.error("Error streaming JSON (items written: {})", itemCount, ex);
                    } else {
                      log.debug("Stream closed during JSON output", ex);
                    }
                  } finally {
                    try {
                      jsonStreamResult.close();
                    } catch (IOException ex) {
                      log.warn("Error closing stream", ex);
                    }
                  }
                })
        .header("Content-Type", MediaType.APPLICATION_JSON)
        .build();
  }

  /**
   * Build proxy URL from request URI
   *
   * @param uriInfo Request URI info
   * @return Proxy URL
   */
  private String buildProxyUrl(UriInfo uriInfo) {
    var baseUri = uriInfo.getBaseUri();
    return baseUri.getScheme()
        + "://"
        + baseUri.getHost()
        + (baseUri.getPort() > 0
                && baseUri.getPort() != (baseUri.getScheme().equals("https") ? 443 : 80)
            ? ":" + baseUri.getPort()
            : "");
  }
}
