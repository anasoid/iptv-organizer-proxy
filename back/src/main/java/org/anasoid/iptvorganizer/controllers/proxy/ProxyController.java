package org.anasoid.iptvorganizer.controllers.proxy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.dto.HttpRequestDto;
import org.anasoid.iptvorganizer.dto.RequestType;
import org.anasoid.iptvorganizer.exceptions.ForbiddenException;
import org.anasoid.iptvorganizer.exceptions.UnauthorizedException;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.services.xtream.XtreamUserService;
import org.anasoid.iptvorganizer.utils.TunnelUtils;

/**
 * Proxy Controller
 *
 * <p>Handles stream proxying with base64 URL decoding and optional redirect following
 *
 * <p>Endpoint: GET /proxy/{username}/{password}?url={base64_encoded_url}
 *
 * <p>This controller is used when `disablestreamproxy=false`. The StreamDataController encodes the
 * upstream URL in base64 and redirects to this endpoint, which then streams the content from the
 * upstream source.
 */
@Slf4j
@Path("/proxy")
@ApplicationScoped
public class ProxyController {

  @Inject XtreamUserService xtreamUserService;
  @Inject TunnelUtils tunnelUtils;

  /**
   * Handle proxy stream request
   *
   * <p>Endpoint: GET /proxy/{username}/{password}?url={base64_encoded_url}
   *
   * @param username Client username
   * @param password Client password
   * @param encodedUrl Base64 encoded upstream URL
   * @param httpHeaders Client request headers
   * @return Stream response with content from upstream
   */
  @GET
  @Path("/{username}/{password}")
  public Response handleProxyRequest(
      @PathParam("username") String username,
      @PathParam("password") String password,
      @QueryParam("url") String encodedUrl,
      @jakarta.ws.rs.core.Context HttpHeaders httpHeaders) {

    try {
      // Validate encoded URL parameter
      if (encodedUrl == null || encodedUrl.isEmpty()) {
        log.warn("Proxy request with missing URL");
        return Response.status(Response.Status.BAD_REQUEST).build();
      }

      // Validate and authenticate client
      var authResult = xtreamUserService.authenticateAndValidateClient(username, password);
      Client client = authResult.getClient();
      Source source = authResult.getSource();

      // Decode URL
      String decodedUrl = decodeUrl(encodedUrl);
      log.debug("Proxy request - user: {}, decodedUrl: {}", username, decodedUrl);

      // Stream content from upstream
      return tunnelUtils.streamFromUpstream(
          new HttpRequestDto(decodedUrl, RequestType.STREAM, httpHeaders),
          client,
          source,
          tunnelUtils.buildHttpOptions(client, source).followRedirects(true).build(),
          tunnelUtils.buildProxyOptions(client, source));

    } catch (UnauthorizedException ex) {
      log.warn("Proxy request unauthorized: {}", ex.getMessage());
      return Response.status(Response.Status.UNAUTHORIZED).build();
    } catch (ForbiddenException ex) {
      log.warn("Proxy request forbidden: {}", ex.getMessage());
      return Response.status(Response.Status.FORBIDDEN).build();
    } catch (IllegalArgumentException ex) {
      log.warn("Invalid proxy URL encoding: {}", ex.getMessage());
      return Response.status(Response.Status.BAD_REQUEST).build();
    } catch (Exception ex) {
      log.error("Error handling proxy request: {}", ex.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Decode base64 URL
   *
   * @param encodedUrl Base64 encoded URL
   * @return Decoded URL
   * @throws IllegalArgumentException if decoding fails
   */
  private String decodeUrl(String encodedUrl) {
    try {
      // URL decode first (in case it's URL encoded)
      String urlDecoded = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8);

      // Then base64 decode
      byte[] decodedBytes = Base64.getUrlDecoder().decode(urlDecoded);
      return new String(decodedBytes, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid base64 encoding in URL parameter", ex);
    }
  }
}
