package org.anasoid.iptvorganizer.services;

import org.anasoid.iptvorganizer.models.LiveStream;
import org.anasoid.iptvorganizer.repositories.LiveStreamRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LiveStreamService extends BaseService<LiveStream, LiveStreamRepository> {

    @Inject
    LiveStreamRepository repository;

    @Override
    protected LiveStreamRepository getRepository() {
        return repository;
    }

    @Override
    public Uni<Long> create(LiveStream stream) {
        if (stream.getSourceId() == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("Source ID is required"));
        }
        if (stream.getName() == null || stream.getName().isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("Name is required"));
        }
        return repository.insert(stream);
    }
}
