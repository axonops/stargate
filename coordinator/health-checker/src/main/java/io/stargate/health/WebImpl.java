package io.stargate.health;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.stargate.core.metrics.api.HttpMetricsTagProvider;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.metrics.api.MetricsScraper;

public class WebImpl {
  private final Metrics metrics;
  private final MetricsScraper metricsScraper;
  private final HttpMetricsTagProvider httpMetricsTagProvider;
  private final HealthCheckRegistry healthCheckRegistry;
  private Server server;

  public WebImpl(
      Metrics metrics,
      MetricsScraper metricsScraper,
      HttpMetricsTagProvider httpMetricsTagProvider,
      HealthCheckRegistry healthCheckRegistry) {
    this.metrics = metrics;
    this.metricsScraper = metricsScraper;
    this.httpMetricsTagProvider = httpMetricsTagProvider;
    this.healthCheckRegistry = healthCheckRegistry;
  }

  public void start() throws Exception {
    server =
        new Server(
            new BundleService(),
            metrics,
            metricsScraper,
            httpMetricsTagProvider,
            healthCheckRegistry);
    server.run("server", "health-checker-config.yaml");
  }

  public void stop() throws Exception {
    // Server shutdown is handled by the dropwizard framework
  }
}
