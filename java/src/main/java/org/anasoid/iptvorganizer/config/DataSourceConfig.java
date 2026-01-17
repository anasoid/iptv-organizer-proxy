package org.anasoid.iptvorganizer.config;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.logging.Logger;

/**
 * DataSource configuration - Quarkus JDBC datasources are automatically configured via
 * application.properties. This class is kept for compatibility but datasources are provided by
 * Quarkus via @Inject javax.sql.DataSource.
 */
@ApplicationScoped
public class DataSourceConfig {

  private static final Logger LOGGER = Logger.getLogger(DataSourceConfig.class.getName());

  public DataSourceConfig() {
    LOGGER.info("DataSourceConfig initialized - using Quarkus JDBC datasource configuration");
  }
}
