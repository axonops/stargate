/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.stargate.core.metrics.impl;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.BaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Module that provides metrics services. */
public class MetricsModule extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(MetricsModule.class);

  private MetricRegistry metricRegistry;
  private HealthCheckRegistry healthCheckRegistry;
  private Metrics metricsImpl;

  public MetricsModule() {
    super("MetricsModule");
  }

  @Override
  protected void createServices() throws Exception {
    logger.info("Initializing metrics services");

    // Create metrics implementation (it creates its own MetricRegistry)
    metricsImpl = new MetricsImpl();

    // Get registry from metrics implementation
    metricRegistry = metricsImpl.getRegistry();

    // Create health check registry
    healthCheckRegistry = new HealthCheckRegistry();

    // Register services
    register(MetricRegistry.class, metricRegistry);
    register(HealthCheckRegistry.class, healthCheckRegistry);
    register(Metrics.class, metricsImpl);

    // MetricsImpl also implements MetricsScraper, so register it
    register(
        io.stargate.core.metrics.api.MetricsScraper.class,
        (io.stargate.core.metrics.api.MetricsScraper) metricsImpl);

    // Register HttpMetricsTagProvider (simple implementation for now)
    register(
        io.stargate.core.metrics.api.HttpMetricsTagProvider.class,
        new io.stargate.core.metrics.api.HttpMetricsTagProvider() {
          @Override
          public io.micrometer.core.instrument.Tags getRequestTags(
              java.util.Map<String, java.util.List<String>> headers) {
            return io.micrometer.core.instrument.Tags.empty();
          }
        });

    logger.info("Metrics services initialized");
  }
}
