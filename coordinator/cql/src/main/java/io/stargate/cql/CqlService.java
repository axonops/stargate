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
package io.stargate.cql;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.stargate.auth.AuthenticationService;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import io.stargate.cql.impl.CqlImpl;
import io.stargate.db.Persistence;
import io.stargate.db.PersistenceConstants;
import io.stargate.db.metrics.api.ClientInfoMetricsTagProvider;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import org.apache.cassandra.stargate.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service implementation for CQL (Cassandra Query Language) support.s */
public class CqlService extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(CqlService.class);

  public static final ObjectMapper mapper =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);

  private static final boolean USE_AUTH_SERVICE =
      Boolean.parseBoolean(System.getProperty("stargate.cql_use_auth_service", "false"));

  private CqlImpl cql;

  public CqlService() {
    super("CQL", true);
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    List<ServiceDependency<?>> deps = new ArrayList<>();

    // Required dependencies
    deps.add(ServiceDependency.required(Metrics.class));
    deps.add(ServiceDependency.required(ClientInfoMetricsTagProvider.class));
    deps.add(
        ServiceDependency.required(
            Persistence.class, "Identifier", PersistenceConstants.PERSISTENCE_IDENTIFIER));

    // Optional authentication dependency
    if (USE_AUTH_SERVICE) {
      deps.add(
          ServiceDependency.required(
              AuthenticationService.class,
              "AuthIdentifier",
              System.getProperty("stargate.auth_id", "AuthTableBasedService")));
    }

    return deps;
  }

  @Override
  protected void createServices() throws Exception {
    if (cql != null) {
      logger.warn("CQL service already created");
      return;
    }

    // Get required dependencies
    Metrics metrics = getService(Metrics.class);
    ClientInfoMetricsTagProvider clientInfoTagProvider =
        getService(ClientInfoMetricsTagProvider.class);
    Persistence persistence =
        getService(Persistence.class, "Identifier", PersistenceConstants.PERSISTENCE_IDENTIFIER);

    // Get optional authentication service
    AuthenticationService authentication = null;
    if (USE_AUTH_SERVICE) {
      authentication =
          getService(
              AuthenticationService.class,
              "AuthIdentifier",
              System.getProperty("stargate.auth_id", "AuthTableBasedService"));
    }

    // Create and start CQL implementation
    Config config = makeConfig();
    cql = new CqlImpl(config, persistence, metrics, authentication, clientInfoTagProvider);
    cql.start();

    logger.info("CQL service created and started");
  }

  @Override
  protected void stopServices() throws Exception {
    if (cql != null) {
      logger.info("Stopping CQL service");
      cql.stop();
      cql = null;
    }
  }

  private static Config makeConfig() {
    try {
      Config c;

      String cqlConfigPath = System.getProperty("stargate.cql.config_path", "");
      if (cqlConfigPath.isEmpty()) {
        c = new Config();
      } else {
        File configFile = new File(cqlConfigPath);
        c = mapper.readValue(configFile, Config.class);
      }

      String listenAddress =
          System.getProperty(
              "stargate.listen_address", InetAddress.getLocalHost().getHostAddress());

      if (!Boolean.getBoolean("stargate.bind_to_listen_address")) listenAddress = "0.0.0.0";

      Integer cqlPort = Integer.getInteger("stargate.cql_port", 9042);

      c.rpc_address = listenAddress;
      c.native_transport_port = cqlPort;

      c.native_transport_max_concurrent_connections =
          Long.getLong("stargate.cql.native_transport_max_concurrent_connections", -1);
      c.native_transport_max_concurrent_connections_per_ip =
          Long.getLong("stargate.cql.native_transport_max_concurrent_connections_per_ip", -1);
      c.native_transport_max_concurrent_requests_in_bytes =
          Long.getLong(
              "stargate.cql.native_transport_max_concurrent_requests_in_bytes",
              Runtime.getRuntime().maxMemory() / 10);
      c.native_transport_max_concurrent_requests_in_bytes_per_ip =
          Long.getLong(
              "stargate.cql.native_transport_max_concurrent_requests_in_bytes_per_ip",
              Runtime.getRuntime().maxMemory() / 40);
      c.native_transport_flush_in_batches_legacy =
          Boolean.getBoolean("stargate.cql.native_transport_flush_in_batches_legacy");
      return c;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
