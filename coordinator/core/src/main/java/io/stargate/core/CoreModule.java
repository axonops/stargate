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
package io.stargate.core;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.stargate.core.metrics.api.HttpMetricsTagProvider;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.metrics.api.MetricsScraper;
import io.stargate.core.metrics.api.NoopHttpMetricsTagProvider;
import io.stargate.core.metrics.impl.MetricsImpl;
import io.stargate.core.services.BaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Core module that provides basic services. */
public class CoreModule extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(CoreModule.class);

  /**
   * Id if the {@link io.stargate.core.metrics.api.HttpMetricsTagProvider}. If not set, this module
   * will register a default impl.
   */
  private static final String HTTP_TAG_PROVIDER_ID =
      System.getProperty("stargate.metrics.http_tag_provider.id");

  public CoreModule() {
    super("core-services");
  }

  @Override
  protected void createServices() throws Exception {
    logger.info("Initializing core module services");

    // Create and register metrics implementation
    MetricsImpl metricsImpl = new MetricsImpl();
    register(Metrics.class, metricsImpl);
    register(MetricsScraper.class, metricsImpl);
    logger.info("Registered Metrics and MetricsScraper services");

    // Register health check registry
    register(HealthCheckRegistry.class, new HealthCheckRegistry());
    logger.info("Registered HealthCheckRegistry");

    // register default http tag provider if we are not using any special one
    if (null == HTTP_TAG_PROVIDER_ID) {
      HttpMetricsTagProvider provider = new NoopHttpMetricsTagProvider();
      register(HttpMetricsTagProvider.class, provider);
      logger.info("Registered default HttpMetricsTagProvider");
    }

    logger.info("Core module services initialized");
  }
}
