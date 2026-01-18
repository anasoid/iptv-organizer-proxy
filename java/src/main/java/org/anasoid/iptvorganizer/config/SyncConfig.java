package org.anasoid.iptvorganizer.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Synchronization configuration for batch operations. Configures batch size for transactional
 * database operations.
 */
@ApplicationScoped
public class SyncConfig {

  @ConfigProperty(name = "sync.batch.size", defaultValue = "100")
  Integer batchSize;

  public Integer getBatchSize() {
    return batchSize;
  }
}
