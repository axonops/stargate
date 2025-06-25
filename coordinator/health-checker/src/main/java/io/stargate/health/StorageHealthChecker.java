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
import io.stargate.db.datastore.DataStore;
import io.stargate.db.datastore.DataStoreFactory;
import io.stargate.db.datastore.ResultSet;
import io.stargate.db.datastore.Row;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Health checker for storage connectivity.
 *
 * <p>Note: The original implementation with custom table creation and insert/select operations has
 * been simplified due to changes in the QueryBuilder API. This now performs a simple system query
 * to verify storage connectivity.
 */
public class StorageHealthChecker extends HealthCheck {
  private static final Logger logger = LoggerFactory.getLogger(StorageHealthChecker.class);

  private static final boolean STORAGE_CHECK_ENABLED =
      Boolean.parseBoolean(System.getProperty("stargate.health_check.data_store.enabled", "false"));

  private final DataStoreFactory dataStoreFactory;

  public StorageHealthChecker(DataStoreFactory dataStoreFactory)
      throws ExecutionException, InterruptedException {
    this.dataStoreFactory = dataStoreFactory;
  }

  @Override
  protected Result check() throws Exception {
    if (!STORAGE_CHECK_ENABLED) {
      return Result.healthy("Storage check disabled");
    }

    try {
      DataStore dataStore = dataStoreFactory.createInternal();

      // Perform a simple system query to verify storage connectivity
      ResultSet resultSet =
          dataStore
              .queryBuilder()
              .select()
              .column("cluster_name")
              .column("data_center")
              .from("system", "local")
              .build()
              .execute()
              .get();

      Row row = resultSet.one();
      if (row == null) {
        return Result.unhealthy("Unable to retrieve system information");
      }

      String clusterName = row.getString("cluster_name");
      String dataCenter = row.getString("data_center");

      if (clusterName == null || clusterName.isEmpty()) {
        return Result.unhealthy("Empty cluster name");
      }

      return Result.healthy(
          String.format("Storage is operational - cluster: %s, DC: %s", clusterName, dataCenter));
    } catch (Exception e) {
      logger.warn("Storage check failed with {}", e.getMessage(), e);
      return Result.unhealthy("Unable to access storage: " + e.getMessage());
    }
  }
}
