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
package io.stargate.db.limiter.global;

import io.stargate.core.services.BaseService;
import io.stargate.db.PersistenceConstants;
import io.stargate.db.limiter.RateLimitingManager;
import io.stargate.db.limiter.global.impl.GlobalRateLimitingManager;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A module for the {@link GlobalRateLimitingManager} rate limiting service.
 *
 * <p>For this service to activate, the {@link #IDENTIFIER} value needs to be passed to the {@link
 * PersistenceConstants#RATE_LIMITING_ID_PROPERTY} system property.
 */
public class GlobalRateLimitingModule extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(GlobalRateLimitingModule.class);

  public static final String IDENTIFIER = "GlobalRateLimiting";
  private static final boolean IS_ENABLED =
      IDENTIFIER.equalsIgnoreCase(
          System.getProperty(PersistenceConstants.RATE_LIMITING_ID_PROPERTY));

  private GlobalRateLimitingManager manager;

  public GlobalRateLimitingModule() {
    super("global-rate-limiting");
  }

  @Override
  protected void createServices() throws Exception {
    // If rate limiting is not enabled (or at least not configured to use this rate limiting
    // service), we avoid creating the manager, as the manager would throw if it doesn't find
    // its configuration.
    if (!IS_ENABLED) {
      logger.info("Global rate limiting is not enabled");
      return;
    }

    logger.info("Creating GlobalRateLimitingManager");
    manager = new GlobalRateLimitingManager();

    Map<String, Object> properties = new HashMap<>();
    properties.put("Identifier", IDENTIFIER);

    register(RateLimitingManager.class, manager, properties);
    logger.info("Registered GlobalRateLimitingManager");
  }

  @Override
  protected void stopServices() throws Exception {
    if (manager != null) {
      logger.info("Stopping GlobalRateLimitingManager");
      manager = null;
    }
  }
}
