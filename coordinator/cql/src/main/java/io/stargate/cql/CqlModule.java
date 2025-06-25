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

import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import io.stargate.db.Persistence;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Module that provides CQL (Cassandra Query Language) services. */
public class CqlModule extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(CqlModule.class);

  public CqlModule() {
    super("CqlModule");
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    return Arrays.asList(
        ServiceDependency.required(Metrics.class),
        ServiceDependency.required(Persistence.class, "Identifier", "Cassandra50Persistence"));
  }

  @Override
  protected void createServices() throws Exception {
    logger.info("Initializing CQL services");

    // Get dependencies
    Metrics metrics = getService(Metrics.class);
    Persistence persistence = getService(Persistence.class, "Identifier", "Cassandra50Persistence");

    // TODO: Start CQL server
    // For now, just log that we're ready
    int cqlPort = Integer.parseInt(System.getProperty("stargate.cql_port", "9042"));
    logger.info("CQL service would start on port {}", cqlPort);

    logger.info("CQL services initialized");
  }
}
