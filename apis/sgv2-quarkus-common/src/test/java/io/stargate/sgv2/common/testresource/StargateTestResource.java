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

package io.stargate.sgv2.common.testresource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.DevServicesContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.common.WithTestResource;
import io.stargate.sgv2.api.common.token.impl.FixedTokenResolver;
import io.stargate.sgv2.common.IntegrationTestUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

/**
 * Quarkus test resource that starts Cassandra and Stargate Coordinator using test containers.
 *
 * <p>Should be used in the integration tests, that should be explicitly annotated with {@link
 * WithTestResource} class: <code>@QuarkusTestResource(value = StargateTestResource.class)</code>
 * (and usually with {@code initArgs} property set as well).
 *
 * <p>When run from IDE, by default it uses container versions specified in {@link Defaults}. If you
 * wish to run locally with different Cassandra version, please set up following system
 * properties:
 *
 * <ol>
 *   <li><code>testing.containers.cassandra-image</code>
 *   <li><code>testing.containers.stargate-image</code>
 *   <li><code>testing.containers.cluster-version</code>
 *   <li><code>testing.containers.cassandra-startup-timeout</code>
 *   <li><code>testing.containers.coordinator-startup-timeout</code>
 * </ol>
 *
 * <p>Note that this resource fetches the auth token and sets it via the properties, using the
 * {@link FixedTokenResolver}.
 */
public class StargateTestResource
    implements QuarkusTestResourceLifecycleManager, DevServicesContext.ContextAware {

  /**
   * Set of defaults for the integration tests, usually used when running from IDE.
   *
   * <p><b>IMPORTANT:</b> If changing defaults please update the default properties in the pom.xml
   * for the cassandra-50 profile.
   */
  interface Defaults {

    String CASSANDRA_IMAGE = "cassandra";
    String CASSANDRA_IMAGE_TAG = "5.0";

    String STARGATE_IMAGE = "stargateio/coordinator-5_0";
    String STARGATE_IMAGE_TAG = "v2";

    String CLUSTER_NAME = "int-test-cluster";
    String CLUSTER_VERSION = "5.0";

    /**
     * Default cassandra start up timeout value in minutes, this can be override using System
     * property testing.containers.cassandra-startup-timeout
     */
    long CASSANDRA_STARTUP_TIMEOUT = 2;

    /**
     * Default coordinator start up timeout value in minutes, this can be override using System
     * property testing.containers.coordinator-startup-timeout
     */
    long COORDINATOR_STARTUP_TIMEOUT = 3;
  }

  private static final Logger LOG = LoggerFactory.getLogger(StargateTestResource.class);

  private Map<String, String> initArgs;

  private Optional<String> containerNetworkId;

  private Network network;

  private GenericContainer<?> cassandraContainer;

  private GenericContainer<?> stargateContainer;

  /**
   * {@inheritDoc}
   *
   * <p><i>Note: container network ID will be present if resource is used
   * with @QuarkusIntegrationTest</i>
   */
  @Override
  public void setIntegrationTestContext(DevServicesContext context) {
    containerNetworkId = context.containerNetworkId();
  }

  @Override
  public void init(Map<String, String> initArgs) {
    this.initArgs = initArgs;
  }

  @Override
  public Map<String, String> start() {
    if (shouldSkip()) {
      return Collections.emptyMap();
    }

    // TODO make reusable after https://github.com/testcontainers/testcontainers-java/pull/4777
    // boolean reuse = containerNetworkId.isEmpty();
    boolean reuse = false;

    ImmutableMap.Builder<String, String> propsBuilder;
    if (containerNetworkId.isPresent()) {
      String networkId = containerNetworkId.get();
      propsBuilder = startWithContainerNetwork(networkId, reuse);
    } else {
      propsBuilder = startWithoutContainerNetwork(reuse);
    }

    // get auth token via exposed coordinator port
    Integer authPort = stargateContainer.getMappedPort(8081);
    String token = getAuthToken(stargateContainer.getHost(), authPort);
    LOG.info("Using auth token %s for integration tests.".formatted(token));

    // add auth token via prop
    propsBuilder.put(IntegrationTestUtils.AUTH_TOKEN_PROP, token);

    propsBuilder.put(IntegrationTestUtils.CASSANDRA_HOST_PROP, cassandraContainer.getHost());
    propsBuilder.put(
        IntegrationTestUtils.CASSANDRA_CQL_PORT_PROP,
        cassandraContainer.getMappedPort(9042).toString());

    // Some Integration tests need to know backend storage version, to work around
    // discrepancies between Cassandra versions
    propsBuilder.put(IntegrationTestUtils.CLUSTER_VERSION_PROP, getClusterVersion());

    // log props and return them
    ImmutableMap<String, String> props = propsBuilder.build();

    // flush all props on the system as well
    props.forEach(System::setProperty);

    // log and return
    LOG.info("Using props map for the integration tests: %s".formatted(props));
    return props;
  }

  // skip the resource creation if tests are initiated against the running app
  private boolean shouldSkip() {
    return System.getProperty("quarkus.http.test-host") != null;
  }

  // start case when api is not a docker image, but app running on host
  public ImmutableMap.Builder<String, String> startWithoutContainerNetwork(boolean reuse) {
    // create network
    Network network = network();

    // cassandra
    cassandraContainer = baseCassandraContainer(reuse);
    cassandraContainer.withNetwork(network);
    cassandraContainer.start();

    stargateContainer = baseCoordinatorContainer(reuse);
    stargateContainer.withNetwork(network).withEnv("SEED", "cassandra");
    stargateContainer.start();

    Integer bridgePort = stargateContainer.getMappedPort(8091);

    //  overwrite bridge port to go via exposed port
    ImmutableMap.Builder<String, String> propsBuilder = ImmutableMap.builder();
    propsBuilder.put("quarkus.grpc.clients.bridge.port", String.valueOf(bridgePort));
    return propsBuilder;
  }

  // start case when api is also a docker image
  private ImmutableMap.Builder<String, String> startWithContainerNetwork(
      String networkId, boolean reuse) {
    // cassandra
    cassandraContainer = baseCassandraContainer(reuse);
    cassandraContainer.withNetworkMode(networkId);
    cassandraContainer.start();
    String cassandraHost = cassandraContainer.getCurrentContainerInfo().getConfig().getHostName();

    // stargate
    stargateContainer = baseCoordinatorContainer(reuse);
    stargateContainer.withNetworkMode(networkId).withEnv("SEED", cassandraHost);
    stargateContainer.start();
    String stargateHost = stargateContainer.getCurrentContainerInfo().getConfig().getHostName();

    // overwrite bridge host to point to the network host
    ImmutableMap.Builder<String, String> propsBuilder = ImmutableMap.builder();
    propsBuilder.put("quarkus.grpc.clients.bridge.host", stargateHost);
    return propsBuilder;
  }

  @Override
  public void stop() {
    if (null != cassandraContainer && !cassandraContainer.isShouldBeReused()) {
      cassandraContainer.stop();
    }
    if (null != stargateContainer && !stargateContainer.isShouldBeReused()) {
      stargateContainer.stop();
    }
  }

  private GenericContainer<?> baseCassandraContainer(boolean reuse) {
    String image = getCassandraImage();
    GenericContainer<?> container =
        new GenericContainer<>(image)
            .withEnv("HEAP_NEWSIZE", "512M")
            .withEnv("MAX_HEAP_SIZE", "2048M")
            .withEnv("CASSANDRA_CGROUP_MEMORY_LIMIT", "true")
            .withEnv(
                "JVM_EXTRA_OPTS",
                "-Dcassandra.skip_wait_for_gossip_to_settle=0 -Dcassandra.load_ring_state=false -Dcassandra.initial_token=1")
            .withNetworkAliases("cassandra")
            .withExposedPorts(7000, 9042)
            .withLogConsumer(
                new Slf4jLogConsumer(LoggerFactory.getLogger("cassandra-docker"))
                    .withPrefix("CASSANDRA"))
            .waitingFor(Wait.forLogMessage(".*Created default superuser role.*\\n", 1))
            .withStartupTimeout(getCassandraStartupTimeout())
            .withReuse(reuse);
    // Cassandra 5.0 only
    container.withEnv("CASSANDRA_CLUSTER_NAME", getClusterName());
    return container;
  }

  private GenericContainer<?> baseCoordinatorContainer(boolean reuse) {
    String image = getStargateImage();
    GenericContainer<?> container =
        new GenericContainer<>(image)
            .withEnv("JAVA_OPTS", "-Xmx1G")
            .withEnv("CLUSTER_NAME", getClusterName())
            .withEnv("CLUSTER_VERSION", getClusterVersion())
            .withEnv("SIMPLE_SNITCH", "true")
            .withEnv("ENABLE_AUTH", "true")
            .withNetworkAliases("coordinator")
            .withExposedPorts(8091, 8081, 8084)
            .withLogConsumer(
                new Slf4jLogConsumer(LoggerFactory.getLogger("coordinator-docker"))
                    .withPrefix("COORDINATOR"))
            .waitingFor(Wait.forHttp("/checker/readiness").forPort(8084).forStatusCode(200))
            .withStartupTimeout(getCoordinatorStartupTimeout())
            .withReuse(reuse);

    return container;
  }

  private Network network() {
    if (null == network) {
      network = Network.newNetwork();
    }
    return network;
  }

  private String getCassandraImage() {
    String image = System.getProperty("testing.containers.cassandra-image");
    if (null == image) {
      return Defaults.CASSANDRA_IMAGE + ":" + Defaults.CASSANDRA_IMAGE_TAG;
    } else {
      return image;
    }
  }

  private String getStargateImage() {
    String image = System.getProperty("testing.containers.stargate-image");
    if (null == image) {
      return Defaults.STARGATE_IMAGE + ":" + Defaults.STARGATE_IMAGE_TAG;
    } else {
      return image;
    }
  }

  private static String getClusterName() {
    return System.getProperty("testing.containers.cluster-name", Defaults.CLUSTER_NAME);
  }

  public static String getClusterVersion() {
    return System.getProperty("testing.containers.cluster-version", Defaults.CLUSTER_VERSION);
  }

  /**
   * @return Time to wait for the Cassandra container to start up before failing
   */
  private Duration getCassandraStartupTimeout() {
    long cassandraStartupTimeout =
        Long.getLong(
            "testing.containers.cassandra-startup-timeout", Defaults.CASSANDRA_STARTUP_TIMEOUT);
    return Duration.ofMinutes(cassandraStartupTimeout);
  }

  /**
   * @return Time to wait for the Coordinator container to start up before failing
   */
  private Duration getCoordinatorStartupTimeout() {
    // 13-Sep-2022, tatu: Earlier baseline of 2 minutes was somehow slightly too low for
    //    REST API on local system (Macbook): 3 minutes appears to work much more reliably
    long coordinatorStartupTimeout =
        Long.getLong(
            "testing.containers.coordinator-startup-timeout", Defaults.COORDINATOR_STARTUP_TIMEOUT);
    return Duration.ofMinutes(coordinatorStartupTimeout);
  }

  private String getAuthToken(String host, int authPort) {
    try {
      // make call to the coordinator auth api
      String json =
          """
              {
                "username":"cassandra",
                "password":"cassandra"
              }
              """;
      URI authUri = new URI("http://%s:%d/v1/auth".formatted(host, authPort));
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(authUri)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      HttpResponse<String> response =
          HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      // map to response and read token
      ObjectMapper objectMapper = new ObjectMapper();
      AuthResponse authResponse = objectMapper.readValue(response.body(), AuthResponse.class);
      return authResponse.authToken;
    } catch (Exception e) {
      throw new RuntimeException("Failed to get Cassandra token for integration tests.", e);
    }
  }

  record AuthResponse(String authToken) {}
}
