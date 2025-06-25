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
package io.stargate.db;

import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import io.stargate.db.datastore.DataStoreFactory;
import io.stargate.db.datastore.PersistenceDataStoreFactory;
import io.stargate.db.limiter.RateLimitingManager;
import io.stargate.db.metrics.api.ClientInfoMetricsTagProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A module for the {@link DataStoreFactory} service and, if enabled, the {@link
 * RateLimitingPersistence} one.
 *
 * <p>For rate limiting to be activated, a service implementing {@link RateLimitingManager} first
 * needs to be activated/registered with an "Identifier" property set to some value X, and the
 * {@link PersistenceConstants#RATE_LIMITING_ID_PROPERTY} system property needs to be set to that X
 * value. This is done to avoid having rate limiting activated by mistake, just because a bundle
 * that activates a {@link RateLimitingManager} is present on the classpath (meaning, setting the
 * {@link PersistenceConstants#RATE_LIMITING_ID_PROPERTY} acts as a confirmation that this rate
 * limiting needs to indeed be activated).
 */
public class DbModule extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(DbModule.class);

  private static final String DB_PERSISTENCE_IDENTIFIER =
      System.getProperty("stargate.persistence_id", "CassandraPersistence");

  private static final String RATE_LIMITING_IDENTIFIER =
      System.getProperty(PersistenceConstants.RATE_LIMITING_ID_PROPERTY, "<none>");

  private static final String CLIENT_INFO_TAG_PROVIDER_ID =
      System.getProperty("stargate.metrics.client_info_tag_provider.id");

  private final ServiceDependency<Persistence> dbPersistence =
      ServiceDependency.required(Persistence.class, "Identifier", DB_PERSISTENCE_IDENTIFIER);

  private final ServiceDependency<RateLimitingManager> rateLimitingManager =
      hasRateLimitingEnabled()
          ? ServiceDependency.required(
              RateLimitingManager.class, "Identifier", RATE_LIMITING_IDENTIFIER)
          : ServiceDependency.optional(RateLimitingManager.class);

  public DbModule() {
    super("persistence-api");
  }

  private static boolean hasRateLimitingEnabled() {
    return !RATE_LIMITING_IDENTIFIER.equalsIgnoreCase("<none>");
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    List<ServiceDependency<?>> deps = new ArrayList<>(2);
    deps.add(dbPersistence);
    if (hasRateLimitingEnabled()) {
      deps.add(rateLimitingManager);
    }

    // Debug logging
    logger.info("IMPORTANT DEBUG: DbModule dependencies:");
    for (ServiceDependency<?> dep : deps) {
      logger.info("IMPORTANT DEBUG:   - {}", dep);
    }
    logger.info("IMPORTANT DEBUG: DB_PERSISTENCE_IDENTIFIER = {}", DB_PERSISTENCE_IDENTIFIER);

    return deps;
  }

  @Override
  protected void createServices() throws Exception {
    logger.info(
        "DbModule createServices starting, looking for Persistence with identifier: {}",
        DB_PERSISTENCE_IDENTIFIER);

    // Try to get the persistence service
    Persistence persistence =
        getService(Persistence.class, "Identifier", DB_PERSISTENCE_IDENTIFIER);
    if (persistence == null) {
      logger.error(
          "Failed to find Persistence service with identifier '{}'", DB_PERSISTENCE_IDENTIFIER);

      // Try to find any persistence service
      Persistence anyPersistence = getService(Persistence.class);
      if (anyPersistence != null) {
        logger.error(
            "Found a Persistence service but without matching identifier: {}", anyPersistence);
      } else {
        logger.error("No Persistence service found at all!");
      }

      throw new RuntimeException(
          String.format(
              "Could not find persistence service with id '%s'", DB_PERSISTENCE_IDENTIFIER));
    }

    if (hasRateLimitingEnabled()) {
      RateLimitingManager rateLimiter =
          getService(RateLimitingManager.class, "Identifier", RATE_LIMITING_IDENTIFIER);
      if (rateLimiter == null) {
        throw new RuntimeException(
            String.format(
                "Could not find rate limiter service with id '%s'", RATE_LIMITING_IDENTIFIER));
      }
      persistence = new RateLimitingPersistence(persistence, rateLimiter);
    }

    // Register persistence with StargatePersistence identifier
    Map<String, Object> persistenceProps = new HashMap<>();
    persistenceProps.put("Identifier", PersistenceConstants.PERSISTENCE_IDENTIFIER);
    register(Persistence.class, persistence, persistenceProps);
    logger.info(
        "Registered Persistence service with identifier: {}",
        PersistenceConstants.PERSISTENCE_IDENTIFIER);

    // Register DataStoreFactory
    register(DataStoreFactory.class, new PersistenceDataStoreFactory(persistence));
    logger.info("Registered PersistenceDataStoreFactory");

    // if no specific client info tag provider, add default
    if (null == CLIENT_INFO_TAG_PROVIDER_ID) {
      register(ClientInfoMetricsTagProvider.class, ClientInfoMetricsTagProvider.DEFAULT);
      logger.info("Registered default ClientInfoMetricsTagProvider");
    }
  }

  @Override
  protected void stopServices() throws Exception {
    logger.info("Stopping DbModule");
    // Cleanup is handled by BaseService
  }
}
