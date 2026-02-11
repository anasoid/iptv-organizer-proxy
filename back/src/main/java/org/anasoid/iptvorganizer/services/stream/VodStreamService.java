package org.anasoid.iptvorganizer.services.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Iterator;
import org.anasoid.iptvorganizer.models.entity.stream.VodStream;
import org.anasoid.iptvorganizer.repositories.stream.VodStreamRepository;
import org.anasoid.iptvorganizer.services.BaseService;

@ApplicationScoped
public class VodStreamService extends BaseService<VodStream, VodStreamRepository> {

  @Inject VodStreamRepository repository;

  @Override
  protected VodStreamRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(VodStream stream) {
    if (stream.getSourceId() == null) {
      throw new IllegalArgumentException("Source ID is required");
    }
    if (stream.getName() == null || stream.getName().isBlank()) {
      throw new IllegalArgumentException("Name is required");
    }
    return repository.insert(stream);
  }

  /** Find all streams by source ID from database */
  public java.util.List<VodStream> findBySourceId(Long sourceId) {
    return repository.findBySourceId(sourceId);
  }

  /** Stream all streams by source ID from database (lazy loading for O(1) memory) */
  public Iterator<VodStream> streamBySourceId(Long sourceId) {
    return repository.streamBySourceId(sourceId);
  }

  /** Find streams by source and category from database */
  public java.util.List<VodStream> findBySourceAndCategory(
      Long sourceId, Integer categoryId, int limit) {
    return repository.findBySourceAndCategory(sourceId, categoryId, limit);
  }

  /** Find stream by source and external stream ID from database */
  public VodStream findBySourceAndStreamId(Long sourceId, Integer streamId) {
    return repository.findBySourceAndStreamId(sourceId, streamId);
  }

  /** Find streams by source ID with pagination from database */
  public java.util.List<VodStream> findBySourceIdPaged(Long sourceId, int page, int limit) {
    return repository.findBySourceIdPaged(sourceId, page, limit);
  }

  /** Count total streams by source ID */
  public long countBySourceId(Long sourceId) {
    return repository.countBySourceId(sourceId);
  }
}
