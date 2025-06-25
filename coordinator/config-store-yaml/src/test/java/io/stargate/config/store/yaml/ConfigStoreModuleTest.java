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
package io.stargate.config.store.yaml;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codahale.metrics.MetricRegistry;
import io.stargate.config.store.api.ConfigStore;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.ServiceManager;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ConfigStoreModuleTest {

  @Test
  public void
      shouldRegisterConfigStoreWhenYamlLocationHasExistingStargateConfigAndMetricsServiceIsPresent()
          throws Exception {

    // given
    ServiceManager serviceManager = mock(ServiceManager.class);
    Metrics metrics = mock(Metrics.class);
    MetricRegistry metricRegistry = new MetricRegistry();

    when(serviceManager.getService(Metrics.class)).thenReturn(metrics);
    when(metrics.getRegistry(ConfigStoreModule.CONFIG_STORE_YAML_METRICS_PREFIX))
        .thenReturn(metricRegistry);

    Path path = getExistingPath();
    ConfigStoreModule module = new ConfigStoreModule(path.toFile().getAbsolutePath());
    module.setServiceManager(serviceManager);

    // when
    module.start();

    // then
    verify(serviceManager)
        .registerService(eq(ConfigStore.class), any(ConfigStoreYaml.class), any(Map.class));
  }

  @Test
  public void shouldRegisterConfigStoreWhenYamlLocationIsEmptyStringAndMetricsServiceIsPresent()
      throws Exception {

    // given
    ServiceManager serviceManager = mock(ServiceManager.class);
    Metrics metrics = mock(Metrics.class);
    MetricRegistry metricRegistry = new MetricRegistry();

    when(serviceManager.getService(Metrics.class)).thenReturn(metrics);
    when(metrics.getRegistry(ConfigStoreModule.CONFIG_STORE_YAML_METRICS_PREFIX))
        .thenReturn(metricRegistry);

    ConfigStoreModule module = new ConfigStoreModule("");
    module.setServiceManager(serviceManager);

    // when
    module.start();

    // then
    verify(serviceManager)
        .registerService(eq(ConfigStore.class), any(ConfigStoreYaml.class), any(Map.class));
  }

  private Path getExistingPath() {
    return Paths.get(
        Objects.requireNonNull(
                ConfigStoreModuleTest.class.getClassLoader().getResource("stargate-config.yaml"))
            .getPath());
  }
}
