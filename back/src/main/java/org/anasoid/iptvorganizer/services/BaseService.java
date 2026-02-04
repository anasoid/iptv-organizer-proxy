package org.anasoid.iptvorganizer.services;

import jakarta.transaction.Transactional;
import java.util.List;
import org.anasoid.iptvorganizer.models.entity.BaseEntity;
import org.anasoid.iptvorganizer.repositories.BaseRepository;

public abstract class BaseService<T extends BaseEntity, R extends BaseRepository<T>> {

  protected abstract R getRepository();

  public T getById(Long id) {
    return getRepository().findById(id);
  }

  public List<T> getAll() {
    return getRepository().findAll();
  }

  public abstract Long create(T entity);

  public void update(T entity) {
    if (entity.getId() == null) {
      throw new IllegalArgumentException("ID is required for update");
    }
    getRepository().update(entity);
  }

  public void delete(Long id) {
    getRepository().delete(id);
  }

  public Long count() {
    return getRepository().count();
  }

  public List<T> getAllPaged(int page, int limit) {
    return getRepository().findAllPaged(page, limit);
  }

  @Transactional
  public T save(T entity) {
    if (entity.getId() == null) {
      Long id = create(entity);
      entity.setId(id);
      return entity;
    } else {
      update(entity);
      return entity;
    }
  }
}
