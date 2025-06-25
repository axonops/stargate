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

import io.stargate.config.store.api.ConfigStore;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A module for YAML-based configuration store. */
public class ConfigStoreModule extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(ConfigStoreModule.class);

  public static final String CONFIG_STORE_YAML_METRICS_PREFIX = "config.store.yaml";
  public static final String CONFIG_STORE_YAML_IDENTIFIER = "ConfigStoreYaml";

  private final String configYamlLocation;
  private final ServiceDependency<Metrics> metricsService =
      ServiceDependency.required(Metrics.class);

  // for testing purpose
  public ConfigStoreModule(String configYamlLocation) {
    super("config-store-yaml");
    this.configYamlLocation = configYamlLocation;
  }

  public ConfigStoreModule() {
    this(
        System.getProperty(
            "stargate.config_store.yaml.location", "/etc/stargate/stargate-config.yaml"));
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    return Collections.singletonList(metricsService);
  }

  @Override
  protected void createServices() throws Exception {
    Metrics metrics = getService(Metrics.class);

    Map<String, Object> props = new HashMap<>();
    props.put("ConfigStoreIdentifier", CONFIG_STORE_YAML_IDENTIFIER);

    logger.info("Creating Config Store YAML for config file location: {}", configYamlLocation);
    ConfigStoreYaml configStore =
        new ConfigStoreYaml(
            Paths.get(configYamlLocation), metrics.getRegistry(CONFIG_STORE_YAML_METRICS_PREFIX));

    register(ConfigStore.class, configStore, props);
    logger.info("Registered ConfigStoreYaml");
  }

  @Override
  protected void stopServices() throws Exception {
    logger.info("Stopping ConfigStoreModule");
    // Cleanup is handled by BaseService
  }
}
