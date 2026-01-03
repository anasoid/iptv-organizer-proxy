package org.anasoid.iptvorganizer.repositories;

import org.anasoid.iptvorganizer.models.Source;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SourceRepository extends BaseRepository<Source> {

    @Override
    protected String getTableName() {
        return "sources";
    }

    @Override
    public Uni<Long> insert(Source source) {
        String sql = "INSERT INTO sources (name, url, username, password, sync_interval, sync_status, is_active, enableproxy, disablestreamproxy, stream_follow_location) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Tuple tuple = Tuple.tuple()
            .addString(source.getName())
            .addString(source.getUrl())
            .addString(source.getUsername())
            .addString(source.getPassword())
            .addInteger(source.getSyncInterval())
            .addString(source.getSyncStatus())
            .addBoolean(source.getIsActive())
            .addBoolean(source.getEnableProxy())
            .addBoolean(source.getDisableStreamProxy())
            .addBoolean(source.getStreamFollowLocation());
        return pool.preparedQuery(sql)
            .execute(tuple)
            .map(rowSet -> rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }

    @Override
    public Uni<Void> update(Source source) {
        String sql = "UPDATE sources SET name = ?, url = ?, username = ?, password = ?, sync_interval = ?, last_sync = ?, next_sync = ?, sync_status = ?, is_active = ?, enableproxy = ?, disablestreamproxy = ?, stream_follow_location = ? WHERE id = ?";
        Tuple tuple = Tuple.tuple()
            .addString(source.getName())
            .addString(source.getUrl())
            .addString(source.getUsername())
            .addString(source.getPassword())
            .addInteger(source.getSyncInterval())
            .addLocalDateTime(source.getLastSync())
            .addLocalDateTime(source.getNextSync())
            .addString(source.getSyncStatus())
            .addBoolean(source.getIsActive())
            .addBoolean(source.getEnableProxy())
            .addBoolean(source.getDisableStreamProxy())
            .addBoolean(source.getStreamFollowLocation())
            .addLong(source.getId());
        return pool.preparedQuery(sql)
            .execute(tuple)
            .replaceWithVoid();
    }

    @Override
    protected Source mapRow(Row row) {
        return Source.builder()
            .id(row.getLong("id"))
            .name(row.getString("name"))
            .url(row.getString("url"))
            .username(row.getString("username"))
            .password(row.getString("password"))
            .syncInterval(row.getInteger("sync_interval"))
            .lastSync(row.getLocalDateTime("last_sync"))
            .nextSync(row.getLocalDateTime("next_sync"))
            .syncStatus(row.getString("sync_status"))
            .isActive(row.getBoolean("is_active"))
            .enableProxy(row.getBoolean("enableproxy"))
            .disableStreamProxy(row.getBoolean("disablestreamproxy"))
            .streamFollowLocation(row.getBoolean("stream_follow_location"))
            .createdAt(row.getLocalDateTime("created_at"))
            .updatedAt(row.getLocalDateTime("updated_at"))
            .build();
    }
}
