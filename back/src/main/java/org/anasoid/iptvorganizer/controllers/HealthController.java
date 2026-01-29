package org.anasoid.iptvorganizer.controllers;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/health")
public class HealthController {

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response health() {
    return Response.ok().entity("{\"status\":\"UP\"}").build();
  }
}
