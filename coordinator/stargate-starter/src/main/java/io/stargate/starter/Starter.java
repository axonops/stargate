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
package io.stargate.starter;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.SingleCommand;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.help.License;
import com.github.rvesse.airline.annotations.restrictions.Port;
import com.github.rvesse.airline.annotations.restrictions.Required;
import io.stargate.auth.api.AuthApiModule;
import io.stargate.auth.jwt.AuthJWTServiceActivator;
import io.stargate.auth.table.AuthTableBasedServiceActivator;
import io.stargate.bridge.BridgeService;
import io.stargate.config.store.yaml.ConfigStoreModule;
import io.stargate.core.CoreModule;
import io.stargate.core.metrics.impl.MetricsModule;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceManager;
import io.stargate.core.services.SimpleServiceManager;
import io.stargate.cql.CqlService;
import io.stargate.db.DbModule;
import io.stargate.db.cassandra.Cassandra50PersistenceService;
import io.stargate.db.limiter.global.GlobalRateLimitingModule;
import io.stargate.graphql.GraphqlModule;
import io.stargate.health.HealthCheckerActivator;
import io.stargate.testing.TestingServicesModule;
import io.stargate.web.impl.RestApiService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Stargate starter. This starts all services. */
@License(url = "https://www.apache.org/licenses/LICENSE-2.0")
@Command(name = "Stargate")
public class Starter {
  private static final Logger logger = LoggerFactory.getLogger(Starter.class);

  public static final String STARTED_MESSAGE = "Finished starting services.";

  @Inject protected HelpOption<Starter> help;

  @Required
  @Option(
      name = {"--cluster-name"},
      title = "cluster_name",
      arity = 1,
      description = "Name of backend cluster")
  protected String clusterName = System.getProperty("stargate.cluster_name");

  @Option(
      name = {"--cluster-version"},
      title = "cluster_version",
      arity = 1,
      description = "Version of backend cluster (must be 5.0)")
  protected String clusterVersion = System.getProperty("stargate.cluster_version", "5.0");

  @Option(
      name = {"--listen"},
      title = "listen_address",
      arity = 1,
      description = "IP address to bind to")
  protected String listenHostStr = System.getProperty("stargate.listen_address");

  protected InetAddress listenHost;

  @Option(
      name = {"--seed"},
      title = "seed",
      arity = 1,
      description = "Seed node address")
  protected String seedList = System.getProperty("stargate.seed_list", "127.0.0.1");

  @Option(
      name = {"--dc"},
      title = "datacenter_name",
      arity = 1,
      description = "Datacenter name")
  protected String datacenter = System.getProperty("stargate.datacenter", "datacenter1");

  @Option(
      name = {"--rack"},
      title = "rack_name",
      arity = 1,
      description = "Rack name")
  protected String rack = System.getProperty("stargate.rack", "rack1");

  @Option(
      name = {"--cluster-seed"},
      title = "cluster_seed",
      arity = 1,
      description = "Seed node address of the cluster")
  protected String seed = System.getProperty("stargate.seed", seedList);

  @Option(
      name = {"--enable-auth"},
      title = "enable_auth",
      arity = 1,
      description = "Enable authentication (true/false)")
  protected String enableAuth = System.getProperty("stargate.enable_auth", "true");

  @Option(
      name = {"--cql-port"},
      title = "cql_port",
      arity = 1,
      description = "CQL port")
  @Port
  protected Integer cqlPort = Integer.parseInt(System.getProperty("stargate.cql_port", "9042"));

  @Option(
      name = {"--simple-snitch"},
      title = "simple_snitch",
      arity = 0,
      description = "Use simple snitch")
  protected boolean simpleSnitch =
      Boolean.parseBoolean(System.getProperty("stargate.simple_snitch", "true"));

  private final ServiceManager serviceManager = new SimpleServiceManager();
  private final List<BaseService> services = new ArrayList<>();
  private final CountDownLatch shutdownLatch = new CountDownLatch(1);

  public static void main(String[] args) {
    SingleCommand<Starter> parser = SingleCommand.singleCommand(Starter.class);
    try {
      Starter starter = parser.parse(args);
      starter.run();
    } catch (Exception e) {
      System.err.println("Unable to start Stargate: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  public void run() throws Exception {
    // Validate cluster version
    if (!"5.0".equals(clusterVersion)) {
      throw new IllegalArgumentException("Only Cassandra 5.0 is supported. Got: " + clusterVersion);
    }

    // Parse and validate parameters
    parseParameters();

    // Set system properties
    setSystemProperties();

    // Start service manager
    serviceManager.start();

    // Start all services in order
    startServices();

    // Register shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

    // Print started message
    logger.info(STARTED_MESSAGE);
    System.out.println(STARTED_MESSAGE);

    // Wait for shutdown
    try {
      shutdownLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void parseParameters() throws UnknownHostException {
    // Parse listen address
    if (listenHostStr != null) {
      listenHost = InetAddress.getByName(listenHostStr);
    } else {
      listenHost = InetAddress.getLocalHost();
    }

    // CQL port is already parsed from @Option
  }

  private void setSystemProperties() {
    // CRITICAL: Set join_ring=false as early as possible
    // This must be set before any Cassandra classes are loaded
    System.setProperty("cassandra.join_ring", "false");

    System.setProperty("stargate.cluster_name", clusterName);
    System.setProperty("stargate.cluster_version", clusterVersion);
    System.setProperty("stargate.listen_address", listenHost.getHostAddress());
    System.setProperty("stargate.seed_list", seedList);
    System.setProperty("stargate.datacenter", datacenter);
    System.setProperty("stargate.rack", rack);
    System.setProperty("stargate.seed", seed);
    System.setProperty("stargate.enable_auth", enableAuth);
    System.setProperty("stargate.cql_port", String.valueOf(cqlPort));

    if (simpleSnitch) {
      System.setProperty("stargate.snitch_classname", "org.apache.cassandra.locator.SimpleSnitch");
    }
  }

  private void startServices() throws Exception {
    logger.info("Starting Stargate services...");

    // Get list of services to skip
    Set<String> skipServices = new HashSet<>();
    String skipServicesProp = System.getProperty("stargate.skip_service");
    if (skipServicesProp != null) {
      String[] servicesToSkip = skipServicesProp.split(",");
      for (String service : servicesToSkip) {
        skipServices.add(service.trim().toLowerCase());
      }
    }

    // Create service instances in dependency order
    Map<String, BaseService> serviceMap = new LinkedHashMap<>();

    // 1. Core services
    MetricsModule metricsModule = new MetricsModule();
    metricsModule.setServiceManager(serviceManager);
    serviceMap.put("MetricsModule", metricsModule);

    CoreModule coreModule = new CoreModule();
    coreModule.setServiceManager(serviceManager);
    serviceMap.put("CoreModule", coreModule);

    // Config store (depends on metrics)
    ConfigStoreModule configStoreModule = new ConfigStoreModule();
    configStoreModule.setServiceManager(serviceManager);
    serviceMap.put("ConfigStoreModule", configStoreModule);

    // 2. Persistence service (must be before health-checker)
    Cassandra50PersistenceService persistenceService = new Cassandra50PersistenceService();
    persistenceService.setServiceManager(serviceManager);
    serviceMap.put("Cassandra50PersistenceService", persistenceService);

    // DB module (provides DataStoreFactory and registers persistence as StargatePersistence)
    DbModule dbModule = new DbModule();
    dbModule.setServiceManager(serviceManager);
    serviceMap.put("DbModule", dbModule);

    // 3. Health checker (depends on persistence)
    if (!skipServices.contains("health-checker")) {
      HealthCheckerActivator healthChecker = new HealthCheckerActivator();
      healthChecker.setServiceManager(serviceManager);
      serviceMap.put("HealthCheckerActivator", healthChecker);
    }

    // 4. Auth services
    if (!skipServices.contains("auth-table-based")) {
      AuthTableBasedServiceActivator authTableService = new AuthTableBasedServiceActivator();
      authTableService.setServiceManager(serviceManager);
      serviceMap.put("AuthTableBasedServiceActivator", authTableService);
    }

    if (!skipServices.contains("auth-jwt")) {
      AuthJWTServiceActivator authJwtService = new AuthJWTServiceActivator();
      authJwtService.setServiceManager(serviceManager);
      serviceMap.put("AuthJWTServiceActivator", authJwtService);
    }

    // Auth API service
    if (!skipServices.contains("auth-api")) {
      AuthApiModule authApiModule = new AuthApiModule();
      authApiModule.setServiceManager(serviceManager);
      serviceMap.put("AuthApiModule", authApiModule);
    }

    // 4. Query and Bridge services
    CqlService cqlService = new CqlService();
    cqlService.setServiceManager(serviceManager);
    serviceMap.put("CqlService", cqlService);

    BridgeService bridgeService = new BridgeService();
    bridgeService.setServiceManager(serviceManager);
    serviceMap.put("BridgeService", bridgeService);

    // 5. API services
    if (!skipServices.contains("graphql")) {
      GraphqlModule graphqlModule = new GraphqlModule();
      graphqlModule.setServiceManager(serviceManager);
      serviceMap.put("GraphqlModule", graphqlModule);
    }

    if (!skipServices.contains("rest-api")) {
      RestApiService restApiService = new RestApiService();
      restApiService.setServiceManager(serviceManager);
      serviceMap.put("RestApiService", restApiService);
    }

    // 6. Optional services
    if (!skipServices.contains("testing-services")) {
      TestingServicesModule testingServices = new TestingServicesModule();
      testingServices.setServiceManager(serviceManager);
      serviceMap.put("TestingServicesModule", testingServices);
    }

    if (!skipServices.contains("global-rate-limiting")) {
      GlobalRateLimitingModule rateLimiting = new GlobalRateLimitingModule();
      rateLimiting.setServiceManager(serviceManager);
      serviceMap.put("GlobalRateLimitingModule", rateLimiting);
    }

    // Start each service
    for (Map.Entry<String, BaseService> entry : serviceMap.entrySet()) {
      String name = entry.getKey();
      BaseService service = entry.getValue();

      try {
        logger.info("Starting service: {}", name);
        service.start();
        services.add(service);
        logger.info("Started service: {}", name);
      } catch (Exception e) {
        logger.error("Failed to start service: " + name, e);
        // Stop already started services
        stopServices();
        throw e;
      }
    }

    logger.info("All services started successfully");
  }

  private void stopServices() {
    // Stop services in reverse order
    for (int i = services.size() - 1; i >= 0; i--) {
      BaseService service = services.get(i);
      try {
        logger.info("Stopping service: {}", service.getServiceName());
        service.stop();
        logger.info("Stopped service: {}", service.getServiceName());
      } catch (Exception e) {
        logger.error("Error stopping service: " + service.getServiceName(), e);
      }
    }
    services.clear();
  }

  private void shutdown() {
    logger.info("Shutting down Stargate...");

    // Stop all services
    stopServices();

    // Stop service manager
    try {
      serviceManager.stop();
    } catch (Exception e) {
      logger.error("Error stopping service manager", e);
    }

    // Signal shutdown complete
    shutdownLatch.countDown();

    logger.info("Stargate shutdown complete");
  }
}
