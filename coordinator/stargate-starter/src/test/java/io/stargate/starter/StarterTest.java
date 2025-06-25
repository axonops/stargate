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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.rvesse.airline.SingleCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StarterTest {

  @BeforeEach
  public void setup() {
    // Clear any system properties
    System.clearProperty("stargate.cluster_name");
    System.clearProperty("stargate.cluster_version");
    System.clearProperty("stargate.listen_address");
    System.clearProperty("stargate.seed_list");
  }

  @Test
  public void testCommandLineParsing() throws Exception {
    String[] args = {
      "--cluster-name", "TestCluster",
      "--cluster-version", "5.0",
      "--listen", "127.0.0.1",
      "--cluster-seed", "127.0.0.1:7000",
      "--seed", "127.0.0.1",
      "--dc", "dc1",
      "--rack", "rack1",
      "--cql-port", "9042",
      "--enable-auth", "false",
      "--simple-snitch"
    };

    SingleCommand<Starter> parser = SingleCommand.singleCommand(Starter.class);
    Starter starter = parser.parse(args);

    assertThat(starter.clusterName).isEqualTo("TestCluster");
    assertThat(starter.clusterVersion).isEqualTo("5.0");
    assertThat(starter.listenHostStr).isEqualTo("127.0.0.1");
    assertThat(starter.seed).isEqualTo("127.0.0.1:7000");
    assertThat(starter.seedList).isEqualTo("127.0.0.1");
    assertThat(starter.datacenter).isEqualTo("dc1");
    assertThat(starter.rack).isEqualTo("rack1");
    assertThat(starter.cqlPort).isEqualTo(9042);
    assertThat(starter.enableAuth).isEqualTo("false");
    assertThat(starter.simpleSnitch).isTrue();
  }

  @Test
  public void testVersionValidation() {
    String[] args = {
      "--cluster-name", "TestCluster",
      "--cluster-version", "4.0", // Wrong version
      "--listen", "127.0.0.1",
      "--cluster-seed", "127.0.0.1:7000"
    };

    SingleCommand<Starter> parser = SingleCommand.singleCommand(Starter.class);
    Starter starter = parser.parse(args);

    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              starter.run();
            });

    assertThat(exception.getMessage()).contains("Only Cassandra 5.0 is supported");
  }

  @Test
  public void testRequiredFields() {
    String[] args = {
      // Missing required --cluster-name
      "--cluster-version", "5.0",
      "--listen", "127.0.0.1",
      "--cluster-seed", "127.0.0.1:7000"
    };

    SingleCommand<Starter> parser = SingleCommand.singleCommand(Starter.class);

    // The parser should fail with required field missing
    assertThrows(
        Exception.class,
        () -> {
          parser.parse(args);
        });
  }

  @Test
  public void testDefaultValues() throws Exception {
    String[] args = {
      "--cluster-name", "TestCluster",
      "--cluster-seed", "127.0.0.1:7000"
      // Not providing other fields to test defaults
    };

    SingleCommand<Starter> parser = SingleCommand.singleCommand(Starter.class);
    Starter starter = parser.parse(args);

    // Check defaults
    assertThat(starter.clusterVersion).isEqualTo("5.0"); // Default value
    assertThat(starter.datacenter).isEqualTo("datacenter1"); // Default value
    assertThat(starter.rack).isEqualTo("rack1"); // Default value
    assertThat(starter.cqlPort).isEqualTo(9042); // Default value
    assertThat(starter.enableAuth).isEqualTo("true"); // Default value
  }
}
