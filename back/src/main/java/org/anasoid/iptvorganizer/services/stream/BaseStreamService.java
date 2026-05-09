package org.anasoid.iptvorganizer.services.stream;

import java.util.Iterator;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.repositories.stream.BaseStreamRepository;
import org.anasoid.iptvorganizer.repositories.stream.BaseStreamRepository.StreamQueryOptions;
import org.anasoid.iptvorganizer.services.BaseService;

/*
 * Copyright 2023-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * @author : anasoid
 * Date :   4/11/26
 */
public abstract class BaseStreamService<T extends BaseStream, R extends BaseStreamRepository<T>>
    extends BaseService<T, R> {

  @Override
  public Long create(T stream) {
    return createStream(stream);
  }

  /** Validate and insert a new stream with required field checks */
  private Long createStream(T stream) {
    if (stream.getSourceId() == null) {
      throw new IllegalArgumentException("Source ID is required");
    }
    if (stream.getName() == null || stream.getName().isBlank()) {
      throw new IllegalArgumentException("Name is required");
    }
    return getRepository().insert(stream);
  }

  /** Find all streams by source ID from database */
  public List<T> findBySourceId(Long sourceId) {
    return getRepository().findBySourceId(sourceId);
  }

  /** Stream all streams by source ID from database (lazy loading for O(1) memory) */
  public Iterator<T> streamBySourceId(Long sourceId) {
    return getRepository().streamBySourceId(sourceId);
  }

  /** Find streams by source and category from database */
  public List<T> findBySourceAndCategory(Long sourceId, Integer categoryId, int limit) {
    return getRepository().findBySourceAndCategory(sourceId, categoryId, limit);
  }

  /** Check if category has at least one stream with explicit allow_deny='allow'. */
  public boolean existsAllowedStreamBySourceAndCategory(Long sourceId, Integer categoryId) {
    return getRepository().existsAllowedStreamBySourceAndCategory(sourceId, categoryId);
  }

  /** Find stream by source and external stream ID from database */
  public T findBySourceAndStreamId(Long sourceId, Integer streamId) {
    return getRepository().findBySourceAndStreamId(sourceId, streamId);
  }

  /** Find streams by source ID with pagination from database */
  public List<T> findBySourceIdPaged(Long sourceId, int page, int limit) {
    return getRepository().findBySourceIdPaged(sourceId, page, limit);
  }

  /** Find streams by source ID with pagination and filters from database */
  public List<T> findBySourceIdPagedWithFilters(
      Long sourceId, StreamQueryOptions options, int page, int limit) {
    return getRepository().findBySourceIdPagedWithFilters(sourceId, options, page, limit);
  }

  /** Count total streams by source ID */
  public long countBySourceId(Long sourceId) {
    return getRepository().countBySourceId(sourceId);
  }

  /** Count total streams by source ID with filters */
  public long countBySourceIdWithFilters(Long sourceId, StreamQueryOptions options) {
    return getRepository().countBySourceIdWithFilters(sourceId, options);
  }

  /** Find streams by source ID with pagination and search (case-insensitive, name/labels) */
  public List<T> findBySourceIdPagedWithSearch(Long sourceId, String search, int page, int limit) {
    return getRepository().findBySourceIdPagedWithSearch(sourceId, search, page, limit);
  }

  /** Count total streams by source ID with search (case-insensitive, name/labels) */
  public long countBySourceIdWithSearch(Long sourceId, String search) {
    return getRepository().countBySourceIdWithSearch(sourceId, search);
  }
}
