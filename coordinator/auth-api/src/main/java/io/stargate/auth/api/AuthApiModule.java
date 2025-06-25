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
package io.stargate.auth.api;

import io.stargate.auth.AuthenticationService;
import io.stargate.auth.api.impl.AuthApiRunner;
import io.stargate.core.metrics.api.HttpMetricsTagProvider;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A module for the Auth API service. */
public class AuthApiModule extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(AuthApiModule.class);

  public static final String MODULE_NAME = "authapi";

  private final ServiceDependency<Metrics> metric = ServiceDependency.required(Metrics.class);
  private final ServiceDependency<HttpMetricsTagProvider> httpTagProvider =
      ServiceDependency.required(HttpMetricsTagProvider.class);
  private final ServiceDependency<AuthenticationService> authenticationService =
      ServiceDependency.required(
          AuthenticationService.class,
          "AuthIdentifier",
          System.getProperty("stargate.auth_id", "AuthTableBasedService"));

  private AuthApiRunner runner;

  public AuthApiModule() {
    super("auth-api");
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    return Arrays.asList(metric, httpTagProvider, authenticationService);
  }

  @Override
  protected void createServices() throws Exception {
    logger.info("Starting Auth API service");

    Metrics metrics = getService(Metrics.class);
    HttpMetricsTagProvider tagProvider = getService(HttpMetricsTagProvider.class);
    AuthenticationService authService =
        getService(
            AuthenticationService.class,
            "AuthIdentifier",
            System.getProperty("stargate.auth_id", "AuthTableBasedService"));

    runner = new AuthApiRunner(authService, metrics, tagProvider);

    // Register the runner
    register(AuthApiRunner.class, runner);

    // Start the HTTP server
    runner.start();
    logger.info("Auth API service started");
  }

  @Override
  protected void stopServices() throws Exception {
    if (runner != null) {
      logger.info("Stopping Auth API service");
      // AuthApiRunner doesn't have a stop method - the server shutdown is handled by Dropwizard
      runner = null;
    }
  }
}
