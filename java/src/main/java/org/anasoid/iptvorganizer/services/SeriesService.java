package org.anasoid.iptvorganizer.services;

import org.anasoid.iptvorganizer.models.Series;
import org.anasoid.iptvorganizer.repositories.SeriesRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SeriesService extends BaseService<Series, SeriesRepository> {

    @Inject
    SeriesRepository repository;

    @Override
    protected SeriesRepository getRepository() {
        return repository;
    }

    @Override
    public Uni<Long> create(Series series) {
        if (series.getSourceId() == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Source ID is required"));
        }
        if (series.getName() == null || series.getName().isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Name is required"));
        }
        return repository.insert(series);
    }
}
