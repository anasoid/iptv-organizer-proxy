package org.anasoid.iptvorganizer.services.stream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Iterator;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.repositories.stream.LiveStreamRepository;
import org.anasoid.iptvorganizer.services.BaseService;

@ApplicationScoped
public class LiveStreamService extends BaseService<LiveStream, LiveStreamRepository> {

  @Inject LiveStreamRepository repository;

  @Override
  protected LiveStreamRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(LiveStream stream) {
    if (stream.getSourceId() == null) {
      throw new IllegalArgumentException("Source ID is required");
    }
    if (stream.getName() == null || stream.getName().isBlank()) {
      throw new IllegalArgumentException("Name is required");
    }
    return repository.insert(stream);
  }

  /** Find all streams by source ID from database */
  public java.util.List<LiveStream> findBySourceId(Long sourceId) {
    return repository.findBySourceId(sourceId);
  }

  /** Stream all streams by source ID from database (lazy loading for O(1) memory) */
  public Iterator<LiveStream> streamBySourceId(Long sourceId) {
    return repository.streamBySourceId(sourceId);
  }

  /** Find streams by source and category from database */
  public java.util.List<LiveStream> findBySourceAndCategory(
      Long sourceId, Integer categoryId, int limit) {
    return repository.findBySourceAndCategory(sourceId, categoryId, limit);
  }

  /** Check if category has at least one stream with explicit allow_deny='allow'. */
  public boolean existsAllowedStreamBySourceAndCategory(Long sourceId, Integer categoryId) {
    return repository.existsAllowedStreamBySourceAndCategory(sourceId, categoryId);
  }

  /** Find stream by source and external stream ID from database */
  public LiveStream findBySourceAndStreamId(Long sourceId, Integer streamId) {
    return repository.findBySourceAndStreamId(sourceId, streamId);
  }

  /** Find streams by source ID with pagination from database */
  public java.util.List<LiveStream> findBySourceIdPaged(Long sourceId, int page, int limit) {
    return repository.findBySourceIdPaged(sourceId, page, limit);
  }

  public java.util.List<LiveStream> findBySourceIdPagedWithFilters(
      Long sourceId,
      org.anasoid.iptvorganizer.repositories.stream.BaseStreamRepository.StreamQueryOptions options,
      int page,
      int limit) {
    return repository.findBySourceIdPagedWithFilters(sourceId, options, page, limit);
  }

  /** Count total streams by source ID */
  public long countBySourceId(Long sourceId) {
    return repository.countBySourceId(sourceId);
  }

  public long countBySourceIdWithFilters(
      Long sourceId,
      org.anasoid.iptvorganizer.repositories.stream.BaseStreamRepository.StreamQueryOptions
          options) {
    return repository.countBySourceIdWithFilters(sourceId, options);
  }
}
