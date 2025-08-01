/*
 * Copyright The Stargate Authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.stargate.it.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.stargate.it.BaseIntegrationTest;
import io.stargate.it.TestOrder;
import io.stargate.it.storage.StargateConnectionInfo;
import io.stargate.it.storage.StargateParameters;
import io.stargate.it.storage.StargateSpec;
import io.stargate.testing.TestingServicesModule;
import io.stargate.testing.metrics.TagMeHttpMetricsTagProvider;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.jcip.annotations.NotThreadSafe;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Leftover test class from Stargate V1 which contains tests against modules that have not yet been
 * extracted out of the Coordinator monolith.
 *
 * <p>Tests moved as follows:
 *
 * <ul>
 *   <li>REST API: {@code RestApiMetricsTest}
 * </ul>
 */
@NotThreadSafe
@StargateSpec(parametersCustomizer = "buildParameters")
@Order(TestOrder.LAST)
public class MetricsTest extends BaseIntegrationTest {

  private static String host;

  @SuppressWarnings("unused") // referenced in @StargateSpec
  public static void buildParameters(StargateParameters.Builder builder) {
    builder.putSystemProperties(
        TestingServicesModule.HTTP_TAG_PROVIDER_PROPERTY,
        TestingServicesModule.TAG_ME_HTTP_TAG_PROVIDER);
    builder.putSystemProperties("stargate.metrics.http_server_requests_percentiles", "0.95,0.99");
    builder.putSystemProperties(
        "stargate.metrics.http_server_requests_path_param_tags", "keyspaceName");
    builder.putSystemProperties(
        "stargate.metrics.http_counter_listener.ignore_http_tags_provider", "true");
  }

  @BeforeAll
  public static void setup(StargateConnectionInfo cluster) {
    host = "http://" + cluster.seedAddress();
  }

  @Test
  public void authApiHttpRequestMetrics() throws IOException {
    // call the rest api path with target header
    String path = String.format("%s:8081/v1/auth", host);
    OkHttpClient client = new OkHttpClient().newBuilder().build();
    Request request =
        new Request.Builder()
            .url(path)
            .post(RequestBody.create("{}", MediaType.parse("application/json")))
            .addHeader(TagMeHttpMetricsTagProvider.TAG_ME_HEADER, "test-value")
            .build();

    int status = execute(client, request);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              String result =
                  RestUtils.get("", String.format("%s:8084/metrics", host), HttpStatus.SC_OK);

              List<String> meteredLines =
                  Arrays.stream(result.split(System.getProperty("line.separator")))
                      .filter(line -> line.startsWith("http_server_requests_seconds"))
                      .collect(Collectors.toList());

              assertThat(meteredLines)
                  .anySatisfy(
                      metric ->
                          assertThat(metric)
                              .contains("method=\"POST\"")
                              .contains("module=\"authapi\"")
                              .contains("uri=\"/v1/auth\"")
                              .contains(String.format("status=\"%d\"", status))
                              .contains(TagMeHttpMetricsTagProvider.TAG_ME_KEY + "=\"test-value\"")
                              .contains("quantile=\"0.95\"")
                              .doesNotContain("error"))
                  .anySatisfy(
                      metric ->
                          assertThat(metric)
                              .contains("method=\"POST\"")
                              .contains("module=\"authapi\"")
                              .contains("uri=\"/v1/auth\"")
                              .contains(String.format("status=\"%d\"", status))
                              .contains(TagMeHttpMetricsTagProvider.TAG_ME_KEY + "=\"test-value\"")
                              .contains("quantile=\"0.99\"")
                              .doesNotContain("error"));

              List<String> countedLines =
                  Arrays.stream(result.split(System.getProperty("line.separator")))
                      .filter(line -> line.startsWith("http_server_requests_counter"))
                      .collect(Collectors.toList());

              assertThat(countedLines)
                  .anySatisfy(
                      metric ->
                          assertThat(metric)
                              .contains("error=\"false\"")
                              .contains("module=\"authapi\"")
                              .doesNotContain("method=\"POST\"")
                              .doesNotContain("uri=\"/v1/auth\"")
                              .doesNotContain(String.format("status=\"%d\"", status))
                              .doesNotContain(
                                  TagMeHttpMetricsTagProvider.TAG_ME_KEY + "=\"test-value\""));
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"authapi", "health_checker"})
  public void dropwizardMetricsModule(String module) throws IOException {
    String[] expectedMetricGroups =
        new String[] {"TimeBoundHealthCheck", "io_dropwizard_jersey", "org_eclipse_jetty"};

    String result = RestUtils.get("", String.format("%s:8084/metrics", host), HttpStatus.SC_OK);

    List<String> moduleLines =
        Arrays.stream(result.split(System.getProperty("line.separator")))
            .filter(line -> line.startsWith(module))
            .collect(Collectors.toList());

    for (String metricGroup : expectedMetricGroups) {
      assertThat(moduleLines).anyMatch(line -> line.contains(metricGroup));
    }
  }

  @Test
  public void dropwizardMetricsPersistence() throws IOException {
    String expectedPrefix = "persistence_cassandra_" + backend.clusterVersion().replace('.', '_');

    String result = RestUtils.get("", String.format("%s:8084/metrics", host), HttpStatus.SC_OK);

    List<String> lines =
        Arrays.stream(result.split(System.getProperty("line.separator")))
            .filter(line -> line.startsWith(expectedPrefix))
            .collect(Collectors.toList());

    assertThat(lines).isNotEmpty();
  }

  private int execute(OkHttpClient client, Request request) throws IOException {
    try (Response execute = client.newCall(request).execute()) {
      assertThat(execute.body()).isNotNull();
      assertThat(execute.code()).isNotZero();
      return execute.code();
    }
  }
}
