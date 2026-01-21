package org.anasoid.iptvorganizer.controllers.admin;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.anasoid.iptvorganizer.dto.ClientDTO;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.services.ClientService;
import org.anasoid.iptvorganizer.utils.ResponseUtils;

/** Clients controller CRUD operations for clients with search and filtering */
@Path("/api/clients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class ClientsController extends BaseController {

  @Inject ClientService clientService;

  /** Get all clients with pagination and search GET /api/clients?page=1&limit=20&search= */
  @GET
  public Response getAllClients(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit,
      @QueryParam("search") String search) {

    if (page < 1 || limit < 1) {
      return ResponseUtils.badRequest("Page and limit must be greater than 0");
    }

    try {
      if (search != null && !search.isBlank()) {
        var clients =
            clientService.searchClients(search, page, limit).stream()
                .map(ClientDTO::fromEntity)
                .collect(Collectors.toList());
        long total = clientService.countSearchClients(search);
        var pagination = PaginationMeta.of(page, limit, total);
        return ResponseUtils.okWithPagination(clients, pagination);
      } else {
        var clients =
            clientService.getAllPaged(page, limit).stream()
                .map(ClientDTO::fromEntity)
                .collect(Collectors.toList());
        long total = clientService.count();
        var pagination = PaginationMeta.of(page, limit, total);
        return ResponseUtils.okWithPagination(clients, pagination);
      }
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to fetch clients: " + ex.getMessage());
    }
  }

  /** Get client by ID GET /api/clients/:id */
  @GET
  @Path("/{id}")
  public Response getClient(@PathParam("id") Long id) {
    try {
      Client client = clientService.getById(id);
      if (client != null) {
        return ResponseUtils.ok(ClientDTO.fromEntity(client));
      } else {
        return ResponseUtils.notFound("Client not found");
      }
    } catch (Exception ex) {
      return ResponseUtils.notFound("Client not found");
    }
  }

  /** Create client POST /api/clients */
  @POST
  public Response createClient(Client request) {
    if (request.getUsername() == null || request.getUsername().isBlank()) {
      return ResponseUtils.badRequest("Username is required");
    }

    try {
      Client client =
          Client.builder()
              .sourceId(request.getSourceId())
              .filterId(request.getFilterId())
              .username(request.getUsername())
              .password(request.getPassword())
              .name(request.getName())
              .email(request.getEmail())
              .expiryDate(request.getExpiryDate())
              .isActive(request.getIsActive() != null ? request.getIsActive() : true)
              .hideAdultContent(
                  request.getHideAdultContent() != null ? request.getHideAdultContent() : false)
              .maxConnections(request.getMaxConnections())
              .notes(request.getNotes())
              .createdAt(LocalDateTime.now())
              .updatedAt(LocalDateTime.now())
              .build();

      Client savedClient = clientService.save(client);
      return ResponseUtils.created(ClientDTO.fromEntity(savedClient));
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to create client: " + ex.getMessage());
    }
  }

  /** Update client PUT /api/clients/:id */
  @PUT
  @Path("/{id}")
  public Response updateClient(@PathParam("id") Long id, Client request) {
    try {
      Client client = clientService.getById(id);
      if (client == null) {
        return ResponseUtils.notFound("Client not found");
      }

      if (request.getUsername() != null) client.setUsername(request.getUsername());
      if (request.getPassword() != null) client.setPassword(request.getPassword());
      if (request.getName() != null) client.setName(request.getName());
      if (request.getEmail() != null) client.setEmail(request.getEmail());
      if (request.getExpiryDate() != null) client.setExpiryDate(request.getExpiryDate());
      if (request.getIsActive() != null) client.setIsActive(request.getIsActive());
      if (request.getHideAdultContent() != null)
        client.setHideAdultContent(request.getHideAdultContent());
      if (request.getMaxConnections() != null)
        client.setMaxConnections(request.getMaxConnections());
      if (request.getNotes() != null) client.setNotes(request.getNotes());
      client.setUpdatedAt(LocalDateTime.now());

      clientService.update(client);
      return ResponseUtils.ok(ClientDTO.fromEntity(client));
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to update client: " + ex.getMessage());
    }
  }

  /** Delete client DELETE /api/clients/:id */
  @DELETE
  @Path("/{id}")
  public Response deleteClient(@PathParam("id") Long id) {
    try {
      clientService.delete(id);
      return ResponseUtils.okMessage("Client deleted successfully");
    } catch (Exception ex) {
      return ResponseUtils.serverError("Failed to delete client: " + ex.getMessage());
    }
  }

  /** Get client logs GET /api/clients/:id/logs?limit=20 */
  @GET
  @Path("/{id}/logs")
  public Response getClientLogs(
      @PathParam("id") Long id, @QueryParam("limit") @DefaultValue("20") int limit) {
    // TODO: Implement client logs retrieval
    return ResponseUtils.okMessage("Client logs retrieved");
  }
}
