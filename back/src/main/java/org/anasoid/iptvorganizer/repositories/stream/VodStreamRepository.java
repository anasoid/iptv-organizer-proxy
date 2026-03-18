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
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.VodStream;
import org.anasoid.iptvorganizer.utils.streaming.JsonStreamResult;
import org.anasoid.iptvorganizer.utils.xtream.XtreamClient;

@ApplicationScoped
public class VodStreamRepository extends BaseStreamRepository<VodStream> {

  @Inject XtreamClient xtreamClient;

  @Override
  protected String getTableName() {
    return "vod_streams";
  }

  @Override
  public JsonStreamResult<Map<?, ?>> fetchExternalData(Source source) {
    return xtreamClient.getVodStreams(source);
  }

  @Override
  protected Long internalInsert(VodStream stream) {
    String sql =
        "INSERT INTO vod_streams (source_id, external_id, num, allow_deny, name, category_id, category_ids, is_adult, labels, data, added_date, release_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      stmt.setLong(1, stream.getSourceId());
      stmt.setInt(2, stream.getExternalId());
      stmt.setObject(3, stream.getNum());
      stmt.setString(4, stream.getAllowDeny() != null ? stream.getAllowDeny().getValue() : null);
      stmt.setString(5, stream.getName());
      stmt.setObject(6, stream.getCategoryId());
      stmt.setString(7, stream.getCategoryIds());
      stmt.setBoolean(8, stream.getIsAdult());
      stmt.setString(9, stream.getLabels());
      stmt.setString(10, stream.getData());
      stmt.setObject(11, stream.getAddedDate());
      stmt.setObject(12, stream.getReleaseDate());
      stmt.executeUpdate();
      Long id = getGeneratedId(stmt);
      stream.setId(id);
      return id;
    } catch (SQLException e) {
      throw new RuntimeException("Failed to insert vod stream", e);
    }
  }

  @Override
  protected void internalUpdate(VodStream stream) {
    String sql =
        "UPDATE vod_streams SET source_id = ?, external_id = ?, num = ?, allow_deny = ?, name = ?, category_id = ?, category_ids = ?, is_adult = ?, labels = ?, data = ?, added_date = ?, release_date = ? WHERE id = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setLong(1, stream.getSourceId());
      stmt.setInt(2, stream.getExternalId());
      stmt.setInt(3, stream.getNum());
      stmt.setString(4, stream.getAllowDeny() != null ? stream.getAllowDeny().getValue() : null);
      stmt.setString(5, stream.getName());
      stmt.setInt(6, stream.getCategoryId());
      stmt.setString(7, stream.getCategoryIds());
      stmt.setBoolean(8, stream.getIsAdult());
      stmt.setString(9, stream.getLabels());
      stmt.setString(10, stream.getData());
      stmt.setObject(11, stream.getAddedDate());
      stmt.setObject(12, stream.getReleaseDate());
      stmt.setLong(13, stream.getId());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to update vod stream", e);
    }
  }

  @Override
  protected VodStream mapRow(ResultSet rs) throws SQLException {
    return VodStream.builder()
        .id(rs.getLong("id"))
        .sourceId(rs.getLong("source_id"))
        .externalId(rs.getInt("external_id"))
        .num(rs.getInt("num"))
        .allowDeny(BaseStream.AllowDenyStatus.fromValue(rs.getString("allow_deny")))
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
