package org.anasoid.iptvorganizer.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;

@ApplicationScoped
public class SourceService extends BaseService<Source, SourceRepository> {

  @Inject SourceRepository repository;

  @Override
  protected SourceRepository getRepository() {
    return repository;
  }

  @Override
  public Long create(Source source) {
    if (source.getName() == null || source.getName().isBlank()) {
      throw new IllegalArgumentException("Name is required");
    }
    if (source.getUrl() == null || source.getUrl().isBlank()) {
      throw new IllegalArgumentException("URL is required");
    }
    if (source.getIsActive() == null) {
      source.setIsActive(true);
    }
    return repository.insert(source);
  }

  /**
   * Check if source has useRedirect configuration
   *
   * @param source The source
   * @return true if useRedirect is explicitly set (not null)
   */
  public boolean hasUseRedirectConfig(Source source) {
    return source != null && source.getUseRedirect() != null;
  }

  /**
   * Check if source has useRedirectXmltv configuration
   *
   * @param source The source
   * @return true if useRedirectXmltv is explicitly set (not null)
   */
  public boolean hasUseRedirectXmltvConfig(Source source) {
    return source != null && source.getUseRedirectXmltv() != null;
  }
}
