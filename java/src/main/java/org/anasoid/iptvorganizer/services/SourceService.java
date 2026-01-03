package org.anasoid.iptvorganizer.services;

import org.anasoid.iptvorganizer.models.Source;
import org.anasoid.iptvorganizer.repositories.SourceRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SourceService extends BaseService<Source, SourceRepository> {

    @Inject
    SourceRepository repository;

    @Override
    protected SourceRepository getRepository() {
        return repository;
    }

    @Override
    public Uni<Long> create(Source source) {
        if (source.getName() == null || source.getName().isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Name is required"));
        }
        if (source.getUrl() == null || source.getUrl().isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("URL is required"));
        }
        if (source.getIsActive() == null) {
            source.setIsActive(true);
        }
        if (source.getSyncStatus() == null) {
            source.setSyncStatus("idle");
        }
        return repository.insert(source);
    }
}
