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
package io.stargate.db.cassandra;

import io.stargate.auth.AuthorizationProcessor;
import io.stargate.auth.AuthorizationService;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import io.stargate.db.Persistence;
import io.stargate.db.cassandra.impl.Cassandra50Persistence;
import io.stargate.db.cassandra.impl.DelegatingAuthorizer;
import io.stargate.db.cassandra.impl.StargateConfigSnitch;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.cassandra.auth.IAuthorizer;
import org.apache.cassandra.auth.PasswordAuthenticator;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.ParameterizedClass;
import org.apache.cassandra.config.YamlConfigurationLoader;
import org.apache.cassandra.dht.Murmur3Partitioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service implementation for Cassandra 5.0 persistence */
public class Cassandra50PersistenceService extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(Cassandra50PersistenceService.class);

  private static final String AUTHZ_PROCESSOR_ID =
      System.getProperty("stargate.authorization.processor.id");

  private Cassandra50Persistence persistence;
  private IAuthorizer authorizer;

  public Cassandra50PersistenceService() {
    super("Cassandra50Persistence", true);
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    return Arrays.asList(
        ServiceDependency.required(Metrics.class),
        ServiceDependency.optional(
            AuthorizationService.class,
            "AuthIdentifier",
            System.getProperty("stargate.auth_id", "AuthTableBasedService")),
        ServiceDependency.optional(
            AuthorizationProcessor.class, "AuthProcessorId", AUTHZ_PROCESSOR_ID));
  }

  @Override
  protected void createServices() throws Exception {
    logger.info("Starting Cassandra 5.0 persistence service");

    // Get dependencies
    Metrics metrics = getService(Metrics.class);
    AuthorizationService authorizationService = getOptionalService(AuthorizationService.class);
    AuthorizationProcessor authorizationProcessor = null;
    if (AUTHZ_PROCESSOR_ID != null) {
      authorizationProcessor =
          getService(AuthorizationProcessor.class, "AuthProcessorId", AUTHZ_PROCESSOR_ID);
    }

    // Set system properties
    System.setProperty("stargate.persistence_id", "CassandraPersistence");

    // CRITICAL: Set join_ring=false BEFORE any Cassandra initialization
    // This must be set before DatabaseDescriptor or CassandraDaemon initialization
    System.setProperty("cassandra.join_ring", "false");

    // Initialize directories
    File baseDir = stargateDirs();

    // Configure Cassandra
    Config config = loadCassandraConfig();

    // Apply Stargate-specific configuration BEFORE initializing DatabaseDescriptor
    applyStargateConfiguration(config, baseDir);

    // Now initialize DatabaseDescriptor with our modified config
    DatabaseDescriptor.daemonInitialization(() -> config);

    // Create DelegatingAuthorizer if authorization service is available
    if (authorizationService != null && authorizationProcessor != null) {
      DelegatingAuthorizer delegatingAuthorizer = new DelegatingAuthorizer();
      delegatingAuthorizer.setProcessor(authorizationProcessor);
      authorizer = delegatingAuthorizer;
      DatabaseDescriptor.setAuthorizer(authorizer);
    }

    // Create and register the persistence service
    persistence = new Cassandra50Persistence();

    // Set authorization service if available
    if (authorizationService != null) {
      persistence.setAuthorizationService(new AtomicReference<>(authorizationService));
    }

    // Initialize the persistence layer - this is crucial!
    persistence.initialize(config);

    // Register services
    Map<String, Object> props = new HashMap<>();
    props.put("Identifier", "CassandraPersistence");
    register(Persistence.class, persistence, props);

    logger.info(
        "Cassandra 5.0 persistence service started successfully - registered as CassandraPersistence");

    // Debug: verify registration
    logger.info("IMPORTANT DEBUG: Verifying Persistence registration...");
    logger.info("IMPORTANT DEBUG: Props used for registration: {}", props);

    Persistence checkPersistence =
        getService(Persistence.class, "Identifier", "CassandraPersistence");
    if (checkPersistence != null) {
      logger.info(
          "IMPORTANT DEBUG: Successfully verified Persistence service is registered with identifier CassandraPersistence");
    } else {
      logger.error("IMPORTANT DEBUG: FAILED to verify Persistence service registration!");
      Persistence anyPersistence = getService(Persistence.class);
      if (anyPersistence != null) {
        logger.error("IMPORTANT DEBUG: Found Persistence service but without matching identifier");
      } else {
        logger.error("IMPORTANT DEBUG: No Persistence service found at all!");
      }
    }
  }

  @Override
  protected void stopServices() throws Exception {
    logger.info("Stopping Cassandra 5.0 persistence service");

    if (persistence != null) {
      persistence.destroy();
      persistence = null;
    }

    logger.info("Cassandra 5.0 persistence service stopped");
  }

  private File stargateDirs() throws IOException {
    File baseDir = new File(System.getProperty("stargate.basedir", "."));

    // Create directories
    File cacheDir = new File(baseDir, "caches");
    File dataDir = new File(baseDir, "data");
    File commitLogDir = new File(baseDir, "commitlog");
    File hintsDir = new File(baseDir, "hints");
    File cdcDir = new File(baseDir, "cdc");
    File metadataDir = new File(baseDir, "metadata");

    cacheDir.mkdirs();
    dataDir.mkdirs();
    commitLogDir.mkdirs();
    hintsDir.mkdirs();
    cdcDir.mkdirs();
    metadataDir.mkdirs();

    // Create system property file
    File triggerDir = new File(System.getProperty("stargate.trigger_dir", "triggers"));
    if (!triggerDir.exists()) {
      triggerDir.mkdirs();
    }

    return baseDir;
  }

  private Config loadCassandraConfig() {
    ClassLoader cl = Cassandra50PersistenceService.class.getClassLoader();
    YamlConfigurationLoader loader = new YamlConfigurationLoader();

    Config config;
    String configPath = System.getProperty("stargate.config_path", "cassandra.yaml");

    try {
      if (new File(configPath).exists()) {
        config = loader.loadConfig(new File(configPath).toURI().toURL());
      } else {
        config = loader.loadConfig(cl.getResource(configPath));
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to load Cassandra configuration", e);
    }

    return config;
  }

  private void applyStargateConfiguration(Config config, File baseDir) throws IOException {
    // Set Stargate-specific directories
    config.saved_caches_directory = new File(baseDir, "caches").getAbsolutePath();
    String[] dataDirs = {new File(baseDir, "data").getAbsolutePath()};
    config.data_file_directories = dataDirs;
    config.commitlog_directory = new File(baseDir, "commitlog").getAbsolutePath();
    config.hints_directory = new File(baseDir, "hints").getAbsolutePath();
    config.cdc_raw_directory = new File(baseDir, "cdc").getAbsolutePath();
    // metadata_directory was removed in Cassandra 5.0

    // Set cluster name
    config.cluster_name = System.getProperty("stargate.cluster_name", "stargate-cassandra");

    // Configure listen address
    String listenAddress = System.getProperty("stargate.listen_address");
    if (listenAddress != null) {
      config.listen_address = listenAddress;
      config.rpc_address = listenAddress;
    } else {
      config.listen_address = InetAddress.getLoopbackAddress().getHostAddress();
      config.rpc_address = InetAddress.getLoopbackAddress().getHostAddress();
    }

    // Configure seed provider - use SimpleSeedProvider for now
    Map<String, String> seedProviderParams = new HashMap<>();
    String seeds = System.getProperty("stargate.seed", "127.0.0.1");
    logger.info("Setting up seed provider with seeds: {}", seeds);
    seedProviderParams.put("seeds", seeds);
    config.seed_provider =
        new ParameterizedClass(
            "org.apache.cassandra.locator.SimpleSeedProvider", seedProviderParams);
    logger.info("Seed provider configured: {}", config.seed_provider);

    // Set snitch
    String snitchClass =
        System.getProperty("stargate.snitch_classname", StargateConfigSnitch.class.getName());
    config.endpoint_snitch = snitchClass;
    if (snitchClass.equals(StargateConfigSnitch.class.getName())) {
      // Configure StargateConfigSnitch through system properties
      System.setProperty("cassandra.dc", System.getProperty("stargate.datacenter", "datacenter1"));
      System.setProperty("cassandra.rack", System.getProperty("stargate.rack", "rack1"));
    }

    // Use password authenticator if auth is enabled
    String enableAuth = System.getProperty("stargate.enable_auth", "false");
    if (Boolean.parseBoolean(enableAuth)) {
      config.authenticator =
          new ParameterizedClass(PasswordAuthenticator.class.getName(), Collections.emptyMap());
      config.authorizer =
          new ParameterizedClass(DelegatingAuthorizer.class.getName(), Collections.emptyMap());
      config.role_manager =
          new ParameterizedClass(
              "org.apache.cassandra.auth.CassandraRoleManager", Collections.emptyMap());
    }

    // Additional settings
    config.partitioner =
        System.getProperty("stargate.partitioner", Murmur3Partitioner.class.getName());
    config.disk_failure_policy = Config.DiskFailurePolicy.best_effort;
    config.repaired_data_tracking_for_range_reads_enabled = false;
    config.repaired_data_tracking_for_partition_reads_enabled = false;

    // Stargate manages its own CQL service, so disable Cassandra's native transport
    config.start_native_transport = false;

    // Set the native transport port (even though we're not starting it)
    config.native_transport_port =
        Integer.parseInt(System.getProperty("stargate.cql_port", "9042"));

    // Stargate is a coordinator-only node, so disable auto bootstrap
    config.auto_bootstrap = false;

    // Cassandra 5.0 specific configuration
    // Storage compatibility mode is already set in cassandra.yaml

    // For Cassandra 5.0, many configuration fields now use spec objects instead of primitives
    // The YamlConfigurationLoader handles parsing these from strings in cassandra.yaml
    // Since we're setting them programmatically, we need to use the defaults that were already
    // loaded from the cassandra.yaml file. The timeout values are already set correctly
    // in our cassandra.yaml resource file.

    // CDC is disabled in our cassandra.yaml
    // File cache configuration - removed in Cassandra 5.0, using defaults
    // SAI configuration - now per-index, not global
    // JMX notification executor pool size - removed in Cassandra 5.0

    // Disable automatic sstable upgrades for coordinator nodes
    config.automatic_sstable_upgrade = false;

    // Leave other configurations as loaded from cassandra.yaml
  }
}
