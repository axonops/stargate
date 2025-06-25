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
package io.stargate.auth.table;

import io.stargate.auth.AuthenticationService;
import io.stargate.auth.AuthorizationService;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import io.stargate.db.datastore.DataStoreFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthTableBasedServiceActivator extends BaseService {
  private static final Logger logger =
      LoggerFactory.getLogger(AuthTableBasedServiceActivator.class);

  public static final String AUTH_TABLE_IDENTIFIER = "AuthTableBasedService";

  private static final Map<String, Object> props = new HashMap<>();

  static {
    props.put("AuthIdentifier", AUTH_TABLE_IDENTIFIER);
  }

  private AuthnTableBasedService authnTableBasedService;
  private AuthzTableBasedService authzTableBasedService;

  public AuthTableBasedServiceActivator() {
    super("authnTableBasedService and authzTableBasedService");
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    return Collections.singletonList(ServiceDependency.required(DataStoreFactory.class));
  }

  @Override
  protected void createServices() throws Exception {
    String authId = System.getProperty("stargate.auth_id", AUTH_TABLE_IDENTIFIER);

    if (!AUTH_TABLE_IDENTIFIER.equals(authId)) {
      logger.info("AuthTableBasedService not enabled. Current auth_id: {}", authId);
      return;
    }

    logger.info("Starting AuthTableBasedService");

    // Get required dependencies
    DataStoreFactory dataStoreFactory = getService(DataStoreFactory.class);

    // Create and configure services
    authnTableBasedService = new AuthnTableBasedService();
    authnTableBasedService.setDataStoreFactory(dataStoreFactory);

    authzTableBasedService = new AuthzTableBasedService();

    // Register services
    register(AuthenticationService.class, authnTableBasedService, props);
    register(AuthorizationService.class, authzTableBasedService, props);

    logger.info("AuthTableBasedService registered successfully");
  }

  @Override
  protected void stopServices() throws Exception {
    logger.info("Stopping AuthTableBasedService");
    // Services will be automatically unregistered by BaseService
  }
}
