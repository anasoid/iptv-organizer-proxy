package org.anasoid.iptvorganizer.controllers;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Path("/admin")
public class AdminSpaController {
  private static final String ADMIN_INDEX_RESOURCE = "META-INF/resources/admin/index.html";

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response getAdminIndex() throws IOException {
    return serveIndex();
  }

  @GET
  @Path("{path: (?!.*\\.[^/]+$).+}")
  @Produces(MediaType.TEXT_HTML)
  public Response getAdminRoute() throws IOException {
    return serveIndex();
  }

  private Response serveIndex() throws IOException {
    try (InputStream inputStream =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(ADMIN_INDEX_RESOURCE)) {
      if (inputStream == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }

      String indexHtml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      return Response.ok(
              indexHtml, MediaType.TEXT_HTML_TYPE.withCharset(StandardCharsets.UTF_8.name()))
          .build();
    }
  }
}
