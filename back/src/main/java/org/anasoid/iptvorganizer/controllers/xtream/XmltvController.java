package org.anasoid.iptvorganizer.controllers.xtream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.*;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.HttpRequestDto;
import org.anasoid.iptvorganizer.dto.RequestType;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.enums.ConnectXmltvMode;
import org.anasoid.iptvorganizer.repositories.ClientRepository;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.anasoid.iptvorganizer.services.ClientService;
import org.anasoid.iptvorganizer.services.xtream.XtreamUserService;
import org.anasoid.iptvorganizer.utils.streaming.StreamModeHandler;
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
  private static final long DEFAULT_TIMEOUT_XMLTV = 120000L;
  @Inject XtreamUserService xtreamUserService;
  @Inject ClientService clientService;
  @Inject ClientRepository clientRepository;
  @Inject SourceRepository sourceRepository;
  @Inject XtreamClient xtreamClient;
  @Inject StreamModeHandler streamModeHandler;

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
      @QueryParam("username") String username,
      @QueryParam("password") String password,
      @Context UriInfo uriInfo,
      @Context HttpHeaders httpHeaders) {

    // Validate and authenticate client
    var authResult = xtreamUserService.authenticateAndValidateClient(username, password);
    Client client = authResult.getClient();
    Source source = authResult.getSource();

    // Resolve XMLTV connection mode
    ConnectXmltvMode xmltvMode = clientService.resolveConnectXmltv(client, source);
    log.info("XMLTV request from client: {}, mode: {}", username, xmltvMode);
    String xmltvUrl = buildXmltvUrl(source);
    HttpRequestDto request = new HttpRequestDto(xmltvUrl, RequestType.XML_TV, httpHeaders);
    return switch (xmltvMode) {
      case REDIRECT -> streamModeHandler.handleRedirectMode(xmltvUrl);
      case NO_PROXY ->
          streamModeHandler.handleTunnelMode(request, client, source, DEFAULT_TIMEOUT_XMLTV);
      case TUNNEL ->
          streamModeHandler.handleTunnelMode(request, client, source, DEFAULT_TIMEOUT_XMLTV);
      case DEFAULT ->
          streamModeHandler.handleTunnelMode(request, client, source, DEFAULT_TIMEOUT_XMLTV);
      default -> streamModeHandler.handleUnknownMode(xmltvMode.toString());
    };
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
        "%s/xmltv.php?username=%s&password=%s",
        baseUrl, source.getUsername(), source.getPassword());
  }
}
