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
package io.stargate.auth;

import io.stargate.core.services.BaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Module that provides authentication and authorization services. */
public class AuthModule extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(AuthModule.class);

  public AuthModule() {
    super("AuthModule");
  }

  @Override
  protected void createServices() throws Exception {
    logger.info("Initializing authentication services");

    // TODO: Implement actual auth service
    // For now, we'll skip registering an AuthorizationService
    // The actual implementation would be AuthTableBasedService or similar
    logger.info("Authentication services initialized (no-op for now)");
  }
}
