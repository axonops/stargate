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
package io.stargate.graphql;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.stargate.auth.AuthenticationService;
import io.stargate.auth.AuthorizationService;
import io.stargate.core.metrics.api.HttpMetricsTagProvider;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import io.stargate.db.Persistence;
import io.stargate.db.PersistenceConstants;
import io.stargate.db.datastore.DataStoreFactory;
import io.stargate.graphql.web.DropwizardServer;
import java.util.Arrays;
import java.util.List;
import net.jcip.annotations.GuardedBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Module for the GraphQL API */
public class GraphqlModule extends BaseService {

  public static final String MODULE_NAME = "graphqlapi";

  private static final Logger LOG = LoggerFactory.getLogger(GraphqlModule.class);

  private static final String AUTH_IDENTIFIER =
      System.getProperty("stargate.auth_id", "AuthTableBasedService");
  private static final boolean ENABLE_GRAPHQL_FIRST =
      Boolean.parseBoolean(System.getProperty("stargate.graphql_first.enabled", "true"));
  private static final boolean ENABLE_GRAPHQL_PLAYGROUND =
      !Boolean.getBoolean("stargate.graphql_playground.disabled");

  @GuardedBy("this")
  private DropwizardServer server;

  public GraphqlModule() {
    super("GraphQL", true);
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    return Arrays.asList(
        ServiceDependency.required(
            Persistence.class, "Identifier", PersistenceConstants.PERSISTENCE_IDENTIFIER),
        ServiceDependency.required(Metrics.class),
        ServiceDependency.required(HttpMetricsTagProvider.class),
        ServiceDependency.required(HealthCheckRegistry.class),
        ServiceDependency.required(AuthenticationService.class, "AuthIdentifier", AUTH_IDENTIFIER),
        ServiceDependency.required(AuthorizationService.class, "AuthIdentifier", AUTH_IDENTIFIER),
        ServiceDependency.required(DataStoreFactory.class));
  }

  @Override
  protected void createServices() throws Exception {
    synchronized (this) {
      if (server == null) {
        LOG.info("Starting GraphQL");

        // Get dependencies
        Persistence persistence =
            getService(
                Persistence.class, "Identifier", PersistenceConstants.PERSISTENCE_IDENTIFIER);
        AuthenticationService authentication =
            getService(AuthenticationService.class, "AuthIdentifier", AUTH_IDENTIFIER);
        AuthorizationService authorization =
            getService(AuthorizationService.class, "AuthIdentifier", AUTH_IDENTIFIER);
        Metrics metrics = getService(Metrics.class);
        HttpMetricsTagProvider httpTagProvider = getService(HttpMetricsTagProvider.class);
        DataStoreFactory dataStoreFactory = getService(DataStoreFactory.class);

        server =
            new DropwizardServer(
                persistence,
                authentication,
                authorization,
                metrics,
                httpTagProvider,
                dataStoreFactory,
                ENABLE_GRAPHQL_FIRST,
                ENABLE_GRAPHQL_PLAYGROUND);

        server.run("server", "graphqlapi-config.yaml");
      }
    }
  }

  @Override
  protected void stopServices() throws Exception {
    synchronized (this) {
      if (server != null) {
        LOG.info("Stopping GraphQL");
        server.stop();
        server = null;
      }
    }
  }
}
