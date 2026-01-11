package org.anasoid.iptvorganizer.controllers.admin;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import org.anasoid.iptvorganizer.dto.ClientDTO;
import org.anasoid.iptvorganizer.dto.response.ApiResponse;
import org.anasoid.iptvorganizer.dto.response.PaginationMeta;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.services.ClientService;

/** Clients controller CRUD operations for clients with search and filtering */
@Path("/api/clients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class ClientsController extends BaseController {

  @Inject ClientService clientService;

  /** Get all clients with pagination and search GET /api/clients?page=1&limit=20&search= */
  @GET
  public Uni<?> getAllClients(
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("limit") @DefaultValue("20") int limit,
      @QueryParam("search") String search) {

    if (page < 1 || limit < 1) {
      return Uni.createFrom().item(ApiResponse.error("Page and limit must be greater than 0"));
    }

    if (search != null && !search.isBlank()) {
      return Uni.combine()
          .all()
          .unis(
              clientService
                  .searchClients(search, page, limit)
                  .map(ClientDTO::fromEntity)
                  .collect()
                  .asList(),
              clientService.countSearchClients(search))
          .asTuple()
          .map(
              tuple -> {
                var clients = tuple.getItem1();
                long total = tuple.getItem2();
                var pagination = PaginationMeta.of(page, limit, total);
                return ApiResponse.successWithPagination(clients, pagination);
              })
          .onFailure()
          .recoverWithItem(ex -> ApiResponse.error("Failed to search clients: " + ex.getMessage()));
    } else {
      return Uni.combine()
          .all()
          .unis(
              clientService.getAllPaged(page, limit).map(ClientDTO::fromEntity).collect().asList(),
              clientService.count())
          .asTuple()
          .map(
              tuple -> {
                var clients = tuple.getItem1();
                long total = tuple.getItem2();
                var pagination = PaginationMeta.of(page, limit, total);
                return ApiResponse.successWithPagination(clients, pagination);
              })
          .onFailure()
          .recoverWithItem(ex -> ApiResponse.error("Failed to fetch clients: " + ex.getMessage()));
    }
  }

  /** Get client by ID GET /api/clients/:id */
  @GET
  @Path("/{id}")
  public Uni<?> getClient(@PathParam("id") Long id) {
    return clientService
        .getById(id)
        .map(
            client ->
                client != null
                    ? ApiResponse.success(ClientDTO.fromEntity(client))
                    : ApiResponse.error("Client not found"))
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Client not found"));
  }

  /** Create client POST /api/clients */
  @POST
  public Uni<?> createClient(Client request) {
    if (request.getUsername() == null || request.getUsername().isBlank()) {
      return Uni.createFrom().item(ApiResponse.error("Username is required"));
    }

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

    return clientService
        .save(client)
        .map(c -> ApiResponse.success(ClientDTO.fromEntity(c)))
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Failed to create client: " + ex.getMessage()));
  }

  /** Update client PUT /api/clients/:id */
  @PUT
  @Path("/{id}")
  public Uni<?> updateClient(@PathParam("id") Long id, Client request) {
    return clientService
        .getById(id)
        .flatMap(
            client -> {
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

              return clientService.update(client).map(v -> client);
            })
        .map(client -> ApiResponse.success(ClientDTO.fromEntity(client)))
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Failed to update client: " + ex.getMessage()));
  }

  /** Delete client DELETE /api/clients/:id */
  @DELETE
  @Path("/{id}")
  public Uni<?> deleteClient(@PathParam("id") Long id) {
    return clientService
        .delete(id)
        .map(v -> ApiResponse.success("Client deleted successfully"))
        .onFailure()
        .recoverWithItem(ex -> ApiResponse.error("Failed to delete client: " + ex.getMessage()));
  }

  /** Get client logs GET /api/clients/:id/logs?limit=20 */
  @GET
  @Path("/{id}/logs")
  public Uni<?> getClientLogs(
      @PathParam("id") Long id, @QueryParam("limit") @DefaultValue("20") int limit) {
    // TODO: Implement client logs retrieval
    return Uni.createFrom().item(ApiResponse.success("Client logs retrieved"));
  }
}
