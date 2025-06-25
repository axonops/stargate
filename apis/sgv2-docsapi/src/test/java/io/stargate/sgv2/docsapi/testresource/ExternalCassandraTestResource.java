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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.stargate.sgv2.docsapi.testresource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.stargate.sgv2.common.IntegrationTestUtils;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test resource for connecting to an external Cassandra 5.0 instance. This resource bypasses the
 * default containerized setup and connects directly to a running Cassandra instance.
 */
public class ExternalCassandraTestResource implements QuarkusTestResourceLifecycleManager {

  private static final Logger LOG = LoggerFactory.getLogger(ExternalCassandraTestResource.class);

  // Static initialization block to load configuration
  private static final Properties CONFIG = new Properties();

  static {
    try (FileInputStream fis = new FileInputStream("stargate-config.properties")) {
      CONFIG.load(fis);
      LOG.info("Loaded configuration from stargate-config.properties");
    } catch (Exception e) {
      LOG.debug("Could not load stargate-config.properties, using defaults: {}", e.getMessage());
    }
  }

  // Configuration for external Cassandra instance
  private static final String CASSANDRA_HOST;
  private static final String CASSANDRA_PORT;

  static {
    String host =
        System.getProperty("test.cassandra.host", CONFIG.getProperty("test.cassandra.host"));
    String port =
        System.getProperty(
            "test.cassandra.port", CONFIG.getProperty("test.cassandra.port", "9042"));

    if (host == null) {
      throw new IllegalStateException(
          "Cassandra host not configured! Please set test.cassandra.host in "
              + "stargate-config.properties or as a system property");
    }

    CASSANDRA_HOST = host;
    CASSANDRA_PORT = port;
  }

  private static final String COORDINATOR_HOST =
      System.getProperty("test.coordinator.host", "localhost");
  private static final String COORDINATOR_AUTH_PORT =
      System.getProperty("test.coordinator.auth.port", "8081");
  private static final String COORDINATOR_BRIDGE_PORT =
      System.getProperty("test.coordinator.bridge.port", "8091");
  private static final String CLUSTER_NAME = System.getProperty("test.cluster.name", "504");
  private static final String CLUSTER_VERSION = System.getProperty("test.cluster.version", "5.0");

  // For no-auth setup, we'll use a fixed token
  private static final String FIXED_AUTH_TOKEN = "cassandra-auth-token";

  @Override
  public Map<String, String> start() {
    LOG.info(
        "Configuring test to use external Cassandra instance at {}:{}",
        CASSANDRA_HOST,
        CASSANDRA_PORT);
    LOG.info("Cluster name: {}, Version: {}", CLUSTER_NAME, CLUSTER_VERSION);

    // Get auth token if coordinator is available
    String authToken = FIXED_AUTH_TOKEN;
    if (isCoordinatorAvailable()) {
      try {
        authToken = getAuthToken();
        LOG.info("Retrieved auth token from coordinator");
      } catch (Exception e) {
        LOG.warn(
            "Failed to get auth token from coordinator, using fixed token: {}", e.getMessage());
      }
    } else {
      LOG.info("Coordinator not available, using fixed auth token");
    }

    // Return configuration properties
    return Map.of(
        // Cassandra connection properties
        IntegrationTestUtils.CASSANDRA_HOST_PROP,
        CASSANDRA_HOST,
        IntegrationTestUtils.CASSANDRA_CQL_PORT_PROP,
        CASSANDRA_PORT,
        IntegrationTestUtils.CLUSTER_VERSION_PROP,
        CLUSTER_VERSION,

        // Auth token
        IntegrationTestUtils.AUTH_TOKEN_PROP,
        authToken,

        // Bridge configuration for gRPC
        "quarkus.grpc.clients.bridge.host",
        COORDINATOR_HOST,
        "quarkus.grpc.clients.bridge.port",
        COORDINATOR_BRIDGE_PORT,

        // Additional properties that might be needed
        "stargate.data-store.ignore-bridge",
        "false");
  }

  @Override
  public void stop() {
    // Nothing to stop for external resources
    LOG.info("Test completed, external resources remain running");
  }

  private boolean isCoordinatorAvailable() {
    try {
      URI healthUri = new URI("http://%s:%s/checker/liveness".formatted(COORDINATOR_HOST, "8084"));
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(healthUri)
              .GET()
              .timeout(java.time.Duration.ofSeconds(5))
              .build();
      HttpResponse<String> response =
          HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }

  private String getAuthToken() {
    try {
      // For no-auth setup, we might not need real authentication
      // But keeping this method in case auth is enabled later
      String json =
          """
          {
            "username":"cassandra",
            "password":"cassandra"
          }
          """;
      URI authUri =
          new URI("http://%s:%s/v1/auth".formatted(COORDINATOR_HOST, COORDINATOR_AUTH_PORT));
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(authUri)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .timeout(java.time.Duration.ofSeconds(5))
              .build();
      HttpResponse<String> response =
          HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        ObjectMapper objectMapper = new ObjectMapper();
        AuthResponse authResponse = objectMapper.readValue(response.body(), AuthResponse.class);
        return authResponse.authToken;
      }
    } catch (Exception e) {
      LOG.warn("Failed to authenticate: {}", e.getMessage());
    }
    return FIXED_AUTH_TOKEN;
  }

  record AuthResponse(String authToken) {}
}
