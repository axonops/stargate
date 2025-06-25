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
import io.stargate.db.Persistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaAgreementChecker extends HealthCheck {
  private static final Logger logger = LoggerFactory.getLogger(SchemaAgreementChecker.class);

  private final Persistence persistence;

  public SchemaAgreementChecker(Persistence persistence) {
    this.persistence = persistence;
  }

  @Override
  protected Result check() {
    try {
      if (persistence == null) {
        return Result.healthy("Persistence is not initialized yet");
      }

      if (persistence.isInSchemaAgreement()) {
        return Result.healthy("All schemas agree");
      }

      // The purpose of the health check is to indicate when restarting this node can help
      // it to achieve schema agreement. Since schema is never actively pulled from other
      // Stargate nodes, restarting will not be effective when local schema agrees with
      // storage nodes and disagrees only with some other Stargate nodes. It is expected
      // that only the Stargate node that does not agree with storage will fail its health
      // check and will be restarted as appropriate.
      if (persistence.isInSchemaAgreementWithStorage()) {
        return Result.healthy("Local schema is in agreement with storage nodes");
      }

      if (persistence.isSchemaAgreementAchievable()) {
        return Result.healthy("Waiting for schema agreement");
      }

      return Result.unhealthy("Schema agreement is not achievable");

    } catch (Exception e) {
      logger.warn("Schema agreement check failed with {}", e.getMessage(), e);
      return Result.unhealthy("Unable to check schema agreement: " + e);
    }
  }
}
