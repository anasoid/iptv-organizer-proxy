package org.anasoid.iptvorganizer.repositories;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import org.anasoid.iptvorganizer.models.Filter;

@ApplicationScoped
public class FilterRepository extends BaseRepository<Filter> {

  @Override
  protected String getTableName() {
    return "filters";
  }

  @Override
  public Uni<Long> insert(Filter filter) {
    String sql =
        "INSERT INTO filters (name, description, filter_config, use_source_filter, favoris) VALUES (?, ?, ?, ?, ?)";
    return pool.preparedQuery(sql)
        .execute(
            Tuple.of(
                filter.getName(),
                filter.getDescription(),
                filter.getFilterConfig(),
                filter.getUseSourceFilter(),
                filter.getFavoris()))
        .map(this::getInsertedId);
  }

  @Override
  public Uni<Void> update(Filter filter) {
    String sql =
        "UPDATE filters SET name = ?, description = ?, filter_config = ?, use_source_filter = ?, favoris = ? WHERE id = ?";
    return pool.preparedQuery(sql)
        .execute(
            Tuple.of(
                filter.getName(),
                filter.getDescription(),
                filter.getFilterConfig(),
                filter.getUseSourceFilter(),
                filter.getFavoris(),
                filter.getId()))
        .replaceWithVoid();
  }

  @Override
  protected Filter mapRow(Row row) {
    return Filter.builder()
        .id(row.getLong("id"))
        .name(row.getString("name"))
        .description(row.getString("description"))
        .filterConfig(row.getString("filter_config"))
        .useSourceFilter(row.getBoolean("use_source_filter"))
        .favoris(row.getString("favoris"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
  }
}
