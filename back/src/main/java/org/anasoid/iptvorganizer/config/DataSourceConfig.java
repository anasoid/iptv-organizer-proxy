package org.anasoid.iptvorganizer.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * DataSource configuration - Quarkus JDBC datasources are automatically configured via
 * application.properties. This class is kept for compatibility but datasources are provided by
 * Quarkus via @Inject javax.sql.DataSource.
 */
@Slf4j
@ApplicationScoped
public class DataSourceConfig {

  public DataSourceConfig() {
    log.info("DataSourceConfig initialized - using Quarkus JDBC datasource configuration");
  }
}
