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
package io.stargate.bridge;

import io.stargate.auth.AuthenticationService;
import io.stargate.auth.AuthorizationService;
import io.stargate.bridge.impl.BridgeImpl;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import io.stargate.db.Persistence;
import io.stargate.db.PersistenceConstants;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service implementation for the Stargate Bridge, which provides gRPC-based access to persistence
 * layer.
 */
public class BridgeService extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(BridgeService.class);

  private BridgeImpl bridge;

  public BridgeService() {
    super("bridge", true);
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    List<ServiceDependency<?>> deps = new ArrayList<>();

    // Required dependencies
    deps.add(ServiceDependency.required(Metrics.class));
    deps.add(
        ServiceDependency.required(
            AuthenticationService.class,
            "AuthIdentifier",
            System.getProperty("stargate.auth_id", "AuthTableBasedService")));
    deps.add(ServiceDependency.required(AuthorizationService.class));
    deps.add(
        ServiceDependency.required(
            Persistence.class, "Identifier", PersistenceConstants.PERSISTENCE_IDENTIFIER));

    return deps;
  }

  @Override
  protected void createServices() throws Exception {
    if (bridge != null) {
      logger.warn("Bridge service already created");
      return;
    }

    // Get required dependencies
    Metrics metrics = getService(Metrics.class);
    AuthenticationService authentication =
        getService(
            AuthenticationService.class,
            "AuthIdentifier",
            System.getProperty("stargate.auth_id", "AuthTableBasedService"));
    AuthorizationService authorization = getService(AuthorizationService.class);
    Persistence persistence =
        getService(Persistence.class, "Identifier", PersistenceConstants.PERSISTENCE_IDENTIFIER);

    // Create and start bridge implementation
    bridge = new BridgeImpl(persistence, metrics, authentication, authorization);
    bridge.start();

    logger.info("Bridge service created and started");
  }

  @Override
  protected void stopServices() throws Exception {
    if (bridge != null) {
      logger.info("Stopping Bridge service");
      bridge.stop();
      bridge = null;
    }
  }
}
