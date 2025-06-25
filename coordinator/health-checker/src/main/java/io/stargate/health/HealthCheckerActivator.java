package io.stargate.health;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.stargate.core.metrics.api.HttpMetricsTagProvider;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.metrics.api.MetricsScraper;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import io.stargate.db.Persistence;
import io.stargate.db.PersistenceConstants;
import io.stargate.db.datastore.DataStoreFactory;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckerActivator extends BaseService {

  private static final Logger log = LoggerFactory.getLogger(HealthCheckerActivator.class);

  public static final String MODULE_NAME = "health-checker";
  public static final String BUNDLES_CHECK_NAME = "bundles";
  public static final String STORAGE_CHECK_NAME = "storage";
  public static final String DATA_STORE_CHECK_NAME = "datastore";
  public static final String SCHEMA_CHECK_NAME = "schema-agreement";

  private WebImpl web;

  public HealthCheckerActivator() {
    super("health-checker");
  }

  @Override
  protected void stopServices() throws Exception {
    log.info("Stopping health-checker...");

    HealthCheckRegistry healthCheckRegistry = getService(HealthCheckRegistry.class);
    healthCheckRegistry.unregister(BUNDLES_CHECK_NAME);
    healthCheckRegistry.unregister(DATA_STORE_CHECK_NAME);
    healthCheckRegistry.unregister(STORAGE_CHECK_NAME);
    healthCheckRegistry.unregister(SCHEMA_CHECK_NAME);

    if (web != null) {
      web.stop();
    }

    log.info("Stopped health-checker");
  }

  @Override
  protected void createServices() throws Exception {
    log.info("Starting health-checker...");

    // Get service dependencies
    HealthCheckRegistry healthCheckRegistry = getService(HealthCheckRegistry.class);
    DataStoreFactory dataStoreFactory = getService(DataStoreFactory.class);
    Persistence persistence =
        getService(Persistence.class, "Identifier", PersistenceConstants.PERSISTENCE_IDENTIFIER);
    Metrics metrics = getService(Metrics.class);
    MetricsScraper metricsScraper = getService(MetricsScraper.class);
    HttpMetricsTagProvider httpTagProvider = getService(HttpMetricsTagProvider.class);

    // Register health checks
    healthCheckRegistry.register(BUNDLES_CHECK_NAME, new ServiceStateChecker());
    healthCheckRegistry.register(
        DATA_STORE_CHECK_NAME, new DataStoreHealthChecker(dataStoreFactory));
    healthCheckRegistry.register(STORAGE_CHECK_NAME, new StorageHealthChecker(dataStoreFactory));
    healthCheckRegistry.register(SCHEMA_CHECK_NAME, new SchemaAgreementChecker(persistence));

    // Start web server
    web = new WebImpl(metrics, metricsScraper, httpTagProvider, healthCheckRegistry);
    web.start();

    log.info("Started health-checker");
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    return Arrays.asList(
        ServiceDependency.required(Metrics.class),
        ServiceDependency.required(MetricsScraper.class),
        ServiceDependency.required(HttpMetricsTagProvider.class),
        ServiceDependency.required(HealthCheckRegistry.class),
        ServiceDependency.required(DataStoreFactory.class),
        ServiceDependency.required(
            Persistence.class, "Identifier", PersistenceConstants.PERSISTENCE_IDENTIFIER));
  }
}
