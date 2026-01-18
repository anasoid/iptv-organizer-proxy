package org.anasoid.iptvorganizer.repositories.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.Series;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

@ApplicationScoped
public class SeriesRepository extends BaseStreamRepository<Series> {
  @Inject XtreamClient xtreamClient;

  @Override
  protected String getTableName() {
    return "series";
  }

  @Override
  public JsonStreamResult<Map<?, ?>> fetchExternalData(Source source) {
    return xtreamClient.getSeries(source);
  }

  @Override
  public Long insert(Series series) {
    String sql =
        "INSERT INTO series (source_id, external_id, num, allow_deny, name, category_id, category_ids, is_adult, labels, data, added_date, release_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setLong(1, series.getSourceId());
      stmt.setInt(2, series.getExternalId());
      stmt.setObject(3, series.getNum());
      stmt.setString(4, series.getAllowDeny());
      stmt.setString(5, series.getName());
      stmt.setObject(6, series.getCategoryId());
      stmt.setString(7, series.getCategoryIds());
      stmt.setBoolean(8, series.getIsAdult());
      stmt.setString(9, series.getLabels());
      stmt.setString(10, series.getData());
      stmt.setObject(11, series.getAddedDate());
      stmt.setObject(12, series.getReleaseDate());
      stmt.executeUpdate();
      Long id = getGeneratedId(stmt);
      series.setId(id);
      return id;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert series", e);
    }
  }

  @Override
  public void update(Series series) {
    String sql =
        "UPDATE series SET source_id = ?, external_id = ?, num = ?, allow_deny = ?, name = ?, category_id = ?, category_ids = ?, is_adult = ?, labels = ?, data = ?, added_date = ?, release_date = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, series.getSourceId());
      stmt.setInt(2, series.getExternalId());
      stmt.setInt(3, series.getNum());
      stmt.setString(4, series.getAllowDeny());
      stmt.setString(5, series.getName());
      stmt.setInt(6, series.getCategoryId());
      stmt.setString(7, series.getCategoryIds());
      stmt.setBoolean(8, series.getIsAdult());
      stmt.setString(9, series.getLabels());
      stmt.setString(10, series.getData());
      stmt.setObject(11, series.getAddedDate());
      stmt.setObject(12, series.getReleaseDate());
      stmt.setLong(13, series.getId());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update series", e);
    }
  }

  @Override
  protected Series mapRow(ResultSet rs) throws SQLException {
    return Series.builder()
        .id(rs.getLong("id"))
        .sourceId(rs.getLong("source_id"))
        .externalId(rs.getInt("external_id"))
        .num(rs.getInt("num"))
        .allowDeny(rs.getString("allow_deny"))
        .name(rs.getString("name"))
        .categoryId(rs.getInt("category_id"))
        .categoryIds(rs.getString("category_ids"))
        .isAdult(rs.getBoolean("is_adult"))
        .labels(rs.getString("labels"))
        .data(rs.getString("data"))
        .addedDate(rs.getObject("added_date", LocalDate.class))
        .releaseDate(rs.getObject("release_date", LocalDate.class))
        .createdAt(rs.getObject("created_at", LocalDateTime.class))
        .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
        .build();
  }
}
