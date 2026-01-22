package org.anasoid.iptvorganizer.controllers.xtream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.exceptions.ForbiddenException;
import org.anasoid.iptvorganizer.exceptions.UnauthorizedException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.http.HttpOptions;
import org.anasoid.iptvorganizer.repositories.ClientRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.services.xtream.XtreamUserService;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

/**
 * XMLTV Controller
 *
 * <p>Handles XMLTV EPG endpoint for Xtream API clients
 *
 * <p>Endpoint: GET /xmltv.php?username={username}&password={password}
 *
 * <p>Returns electronic program guide (EPG) data in XMLTV format for use with Kodi and other IPTV
 * clients.
 */
@Slf4j
@Path("/xmltv.php")
@ApplicationScoped
public class XmltvController {

  private static final int CHUNK_SIZE = 8192;
  private static final String XMLTV_ACTION = "get_xmltv";

  @Inject XtreamUserService xtreamUserService;
  @Inject ClientRepository clientRepository;
  @Inject SourceRepository sourceRepository;
  @Inject XtreamClient xtreamClient;

  /**
   * Get XMLTV EPG data
   *
   * <p>Endpoint: GET /xmltv.php?username={username}&password={password}
   *
   * @param username Client username
   * @param password Client password
   * @return XMLTV formatted EPG data
   */
  @GET
  @Produces(MediaType.APPLICATION_XML)
  public Response getXmltv(
      @QueryParam("username") String username, @QueryParam("password") String password) {

    try {
      // Validate and authenticate client
      var authResult = xtreamUserService.authenticateAndValidateClient(username, password);
      Client client = authResult.getClient();
      Source source = authResult.getSource();

      log.info("XMLTV request from client: " + username);

      // Stream XMLTV data from source
      return streamXmltvData(source, client);

    } catch (UnauthorizedException ex) {
      log.warn("XMLTV request unauthorized: " + ex.getMessage());
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv></tv>")
          .header("Content-Type", MediaType.APPLICATION_XML)
          .build();
    } catch (ForbiddenException ex) {
      log.warn("XMLTV request forbidden: " + ex.getMessage());
      return Response.status(Response.Status.FORBIDDEN)
          .entity("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv></tv>")
          .header("Content-Type", MediaType.APPLICATION_XML)
          .build();
    } catch (Exception ex) {
      log.error("Error handling XMLTV request: " + ex.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("<?xml version=\"1.0\" encoding=\"UTF-8\"?><tv></tv>")
          .header("Content-Type", MediaType.APPLICATION_XML)
          .build();
    }
  }

  /**
   * Stream XMLTV data from source
   *
   * @param source The source
   * @param client The client (for logging)
   * @return Response with streaming XMLTV data
   */
  private Response streamXmltvData(Source source, Client client) {
    return Response.ok(
            (StreamingOutput)
                os -> {
                  InputStream inputStream = null;
                  try {
                    // Build XMLTV URL with action parameter
                    String xmltvUrl = buildXmltvUrl(source);

                    log.info(
                        String.format(
                            "Streaming XMLTV from source: %s for client: %s",
                            source.getName(), client.getUsername()));

                    // Fetch XMLTV data from upstream source
                    HttpOptions options =
                        HttpOptions.builder().timeout(60000L).maxRetries(1).build();

                    org.anasoid.iptvorganizer.utils.streaming.HttpStreamingService
                        httpStreamingService =
                            (org.anasoid.iptvorganizer.utils.streaming.HttpStreamingService)
                                getHttpStreamingService();

                    inputStream = httpStreamingService.streamHttp(xmltvUrl, options);

                    // Stream in chunks
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                      os.write(buffer, 0, bytesRead);
                      os.flush();
                    }

                    log.info(
                        String.format(
                            "XMLTV streaming completed for client: %s", client.getUsername()));

                  } catch (IOException ex) {
                    // Handle client disconnection
                    if (ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
                      log.info(
                          "Client "
                              + client.getUsername()
                              + " disconnected during XMLTV streaming");
                    } else {
                      log.warn(
                          "Error streaming XMLTV for client "
                              + client.getUsername()
                              + ": "
                              + ex.getMessage());
                    }
                  } catch (Exception ex) {
                    log.error("Error streaming XMLTV: " + ex.getMessage());
                  } finally {
                    if (inputStream != null) {
                      try {
                        inputStream.close();
                      } catch (IOException ex) {
                        log.warn("Error closing XMLTV stream: " + ex.getMessage());
                      }
                    }
                  }
                })
        .header("Content-Type", MediaType.APPLICATION_XML)
        .header("Content-Disposition", "inline; filename=\"epg.xml\"")
        .build();
  }

  /**
   * Build XMLTV URL with authentication
   *
   * @param source The source
   * @return Constructed XMLTV URL
   */
  private String buildXmltvUrl(Source source) {
    String baseUrl = source.getUrl().replaceAll("/$", "");
    return String.format(
        "%s/player_api.php?action=%s&username=%s&password=%s",
        baseUrl, XMLTV_ACTION, source.getUsername(), source.getPassword());
  }

  /**
   * Get HTTP streaming service instance
   *
   * <p>This is a workaround for injection - in production, this should be injected properly
   *
   * @return HttpStreamingService instance
   */
  @Inject org.anasoid.iptvorganizer.utils.streaming.HttpStreamingService httpStreamingService;

  private org.anasoid.iptvorganizer.utils.streaming.HttpStreamingService getHttpStreamingService() {
    return httpStreamingService;
  }
}
