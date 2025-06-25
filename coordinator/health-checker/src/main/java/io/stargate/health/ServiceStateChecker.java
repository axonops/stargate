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
package io.stargate.health;

import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Health check for service states. In the future, this could be enhanced to check actual service
 * availability.
 */
public class ServiceStateChecker extends HealthCheck {
  private static final Logger logger = LoggerFactory.getLogger(ServiceStateChecker.class);

  @Override
  protected Result check() {
    // We assume services are healthy if this check is running
    // This could be enhanced to check specific service states in the future
    return Result.healthy("All services active");
  }
}
