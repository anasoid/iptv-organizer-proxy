package org.anasoid.iptvorganizer.repositories;

import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import org.anasoid.iptvorganizer.models.entity.Filter;

@ApplicationScoped
public class FilterRepository extends BaseRepository<Filter> {

  @Override
  protected String getTableName() {
    return "filters";
  }

  @Override
  protected Long internalInsert(Filter filter) {
    String sql =
        "INSERT INTO filters (name, description, filter_config, use_source_filter, favoris) VALUES (?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setString(1, filter.getName());
      stmt.setString(2, filter.getDescription());
      stmt.setString(3, filter.getFilterConfig());
      stmt.setBoolean(4, filter.getUseSourceFilter());
      stmt.setString(5, filter.getFavoris());
      stmt.executeUpdate();
      return getGeneratedId(stmt);
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert filter", e);
    }
  }

  @Override
  protected void internalUpdate(Filter filter) {
    String sql =
        "UPDATE filters SET name = ?, description = ?, filter_config = ?, use_source_filter = ?, favoris = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, filter.getName());
      stmt.setString(2, filter.getDescription());
      stmt.setString(3, filter.getFilterConfig());
      stmt.setBoolean(4, filter.getUseSourceFilter());
      stmt.setString(5, filter.getFavoris());
      stmt.setLong(6, filter.getId());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update filter", e);
    }
  }

  @Override
  protected Filter mapRow(ResultSet rs) throws SQLException {
    return Filter.builder()
        .id(rs.getLong("id"))
        .name(rs.getString("name"))
        .description(rs.getString("description"))
        .filterConfig(rs.getString("filter_config"))
        .useSourceFilter(rs.getBoolean("use_source_filter"))
        .favoris(rs.getString("favoris"))
        .createdAt(rs.getObject("created_at", LocalDateTime.class))
        .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
        .build();
  }
}
