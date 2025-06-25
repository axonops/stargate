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
package io.stargate.web.impl;

import io.stargate.auth.AuthenticationService;
import io.stargate.auth.AuthorizationService;
import io.stargate.core.metrics.api.HttpMetricsTagProvider;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import io.stargate.db.datastore.DataStoreFactory;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service implementation for the REST API module */
public class RestApiService extends BaseService {

  public static final String MODULE_NAME = "restapi";
  private static final Logger logger = LoggerFactory.getLogger(RestApiService.class);

  private RestApiServer server;

  // Service dependencies
  private final ServiceDependency<AuthenticationService> authenticationService;
  private final ServiceDependency<Metrics> metrics = ServiceDependency.required(Metrics.class);
  private final ServiceDependency<HttpMetricsTagProvider> httpTagProvider =
      ServiceDependency.required(HttpMetricsTagProvider.class);
  private final ServiceDependency<AuthorizationService> authorizationService =
      ServiceDependency.required(AuthorizationService.class);
  private final ServiceDependency<DataStoreFactory> dataStoreFactory =
      ServiceDependency.required(DataStoreFactory.class);

  public RestApiService() {
    super(MODULE_NAME, true);

    // Configure authentication service dependency based on system property
    String authId = System.getProperty("stargate.auth_id", "AuthTableBasedService");
    this.authenticationService =
        ServiceDependency.required(AuthenticationService.class, "AuthIdentifier", authId);
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    return Arrays.asList(
        authenticationService, metrics, httpTagProvider, authorizationService, dataStoreFactory);
  }

  @Override
  protected void createServices() throws Exception {
    logger.info("Creating REST API server");

    // Get required services
    AuthenticationService authService =
        getService(
            AuthenticationService.class,
            "AuthIdentifier",
            System.getProperty("stargate.auth_id", "AuthTableBasedService"));
    Metrics metricsService = getService(Metrics.class);
    HttpMetricsTagProvider tagProvider = getService(HttpMetricsTagProvider.class);
    AuthorizationService authzService = getService(AuthorizationService.class);
    DataStoreFactory dsFactory = getService(DataStoreFactory.class);

    // Create and start the server
    server = new RestApiServer(authService, authzService, metricsService, tagProvider, dsFactory);

    server.run("server", "restapi-config.yaml");

    logger.info("REST API server started successfully");
  }

  @Override
  protected void stopServices() throws Exception {
    logger.info("Stopping REST API server");
    if (server != null) {
      // Note: RestApiServer extends DropwizardApplication which doesn't have a clean stop method
      // In a production system, we would need to implement proper shutdown logic
      logger.warn("REST API server shutdown not implemented - server will stop with JVM");
    }
  }
}
