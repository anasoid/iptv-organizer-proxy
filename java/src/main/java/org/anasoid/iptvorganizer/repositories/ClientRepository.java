package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.Client;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClientRepository extends BaseRepository<Client> {

    @Override
    protected String getTableName() {
        return "clients";
    }

    @Override
    public Uni<Long> insert(Client client) {
        String sql = "INSERT INTO clients (source_id, filter_id, username, password, name, email, expiry_date, is_active, hide_adult_content, max_connections, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        io.vertx.mutiny.sqlclient.Tuple tuple = io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(client.getSourceId())
            .addLong(client.getFilterId())
            .addString(client.getUsername())
            .addString(client.getPassword())
            .addString(client.getName())
            .addString(client.getEmail())
            .addLocalDate(client.getExpiryDate())
            .addBoolean(client.getIsActive())
            .addBoolean(client.getHideAdultContent())
            .addInteger(client.getMaxConnections())
            .addString(client.getNotes());
        return pool.preparedQuery(sql)
            .execute(tuple)
            .map(this::getInsertedId);
    }

    @Override
    public Uni<Void> update(Client client) {
        String sql = "UPDATE clients SET source_id = ?, filter_id = ?, username = ?, password = ?, name = ?, email = ?, expiry_date = ?, is_active = ?, hide_adult_content = ?, max_connections = ?, notes = ? WHERE id = ?";
        io.vertx.mutiny.sqlclient.Tuple tuple = io.vertx.mutiny.sqlclient.Tuple.tuple()
            .addLong(client.getSourceId())
            .addLong(client.getFilterId())
            .addString(client.getUsername())
            .addString(client.getPassword())
            .addString(client.getName())
            .addString(client.getEmail())
            .addLocalDate(client.getExpiryDate())
            .addBoolean(client.getIsActive())
            .addBoolean(client.getHideAdultContent())
            .addInteger(client.getMaxConnections())
            .addString(client.getNotes())
            .addLong(client.getId());
        return pool.preparedQuery(sql)
            .execute(tuple)
            .replaceWithVoid();
    }

    @Override
    protected Client mapRow(Row row) {
        return Client.builder()
            .id(row.getLong("id"))
            .sourceId(row.getLong("source_id"))
            .filterId(row.getLong("filter_id"))
            .username(row.getString("username"))
            .password(row.getString("password"))
            .name(row.getString("name"))
            .email(row.getString("email"))
            .expiryDate(row.getLocalDate("expiry_date"))
            .isActive(row.getBoolean("is_active"))
            .hideAdultContent(row.getBoolean("hide_adult_content"))
            .maxConnections(row.getInteger("max_connections"))
            .notes(row.getString("notes"))
            .createdAt(row.getLocalDateTime("created_at"))
            .updatedAt(row.getLocalDateTime("updated_at"))
            .lastLogin(row.getLocalDateTime("last_login"))
            .build();
    }

    /**
     * Search clients by username, name, or email with pagination
     */
    public Multi<Client> searchClients(String search, int page, int limit) {
        String whereClause = "(username LIKE ? OR name LIKE ? OR email LIKE ?)";
        String searchTerm = "%" + search + "%";
        Tuple params = Tuple.of(searchTerm, searchTerm, searchTerm);
        return findWherePaged(whereClause, params, page, limit, "id DESC");
    }

    /**
     * Count clients matching search criteria
     */
    public Uni<Long> countSearchClients(String search) {
        String whereClause = "(username LIKE ? OR name LIKE ? OR email LIKE ?)";
        String searchTerm = "%" + search + "%";
        Tuple params = Tuple.of(searchTerm, searchTerm, searchTerm);
        return countWhere(whereClause, params);
    }
}
