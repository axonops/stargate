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
package io.stargate.sgv2.restapi.service.resources.schemas;

import io.smallrye.mutiny.Uni;
import io.stargate.bridge.grpc.Values;
import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.bridge.proto.QueryOuterClass.Query;
import io.stargate.bridge.proto.Schema;
import io.stargate.sgv2.api.common.config.RequestParams;
import io.stargate.sgv2.api.common.cql.builder.Predicate;
import io.stargate.sgv2.api.common.cql.builder.QueryBuilder;
import io.stargate.sgv2.restapi.config.RestApiUtils;
import io.stargate.sgv2.restapi.service.models.Sgv2SAIIndexAddRequest;
import io.stargate.sgv2.restapi.service.resources.RestResourceBase;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jboss.resteasy.reactive.RestResponse;

public class Sgv2SAIIndexesResourceImpl extends RestResourceBase implements Sgv2SAIIndexesResourceApi {
  
  @Inject
  private Sgv2IndexesResourceApi indexesResource;
  
  @Override
  public Uni<RestResponse<Object>> getAllSAIIndexes(
      String keyspaceName, String tableName, Boolean compactMap) {
    final RequestParams requestParams = RestApiUtils.getRequestParams(restApiConfig, compactMap);
    
    // Query for all indexes - filtering will be done on client side
    // since we can't easily filter by options in CQL
    Query query =
        new QueryBuilder()
            .select()
            .from("system_schema", "indexes")
            .where("keyspace_name", Predicate.EQ, Values.of(keyspaceName))
            .where("table_name", Predicate.EQ, Values.of(tableName))
            .parameters(PARAMETERS_FOR_LOCAL_QUORUM)
            .build();
    
    return getTableAsyncCheckExistence(keyspaceName, tableName, true, Status.BAD_REQUEST)
        .flatMap(table -> executeQueryAsync(query))
        .map(response -> convertRowsToResponse(response, true, requestParams));
  }
  
  @Override
  public Uni<RestResponse<Map<String, Object>>> createSAIIndex(
      String keyspaceName, String tableName, Sgv2SAIIndexAddRequest indexAdd) {
    // Convert SAI-specific request to generic index request and delegate
    return indexesResource.addIndex(keyspaceName, tableName, indexAdd.toGenericRequest());
  }
  
  @Override
  public Uni<RestResponse<Map<String, Object>>> analyzeColumnForSAI(
      String keyspaceName, String tableName, String columnName) {
    
    return getTableAsyncCheckExistence(keyspaceName, tableName, true, Status.BAD_REQUEST)
        .map(table -> {
          // Find the column
          boolean columnFound = table.getColumnsList().stream()
              .anyMatch(col -> col.getName().equals(columnName));
          
          if (!columnFound) {
            throw new WebApplicationException(
                String.format("Column '%s' not found in table '%s'", columnName, tableName),
                Status.NOT_FOUND);
          }
          
          Map<String, Object> analysis = new HashMap<>();
          analysis.put("column", columnName);
          
          Map<String, Object> recommendations = new HashMap<>();
          recommendations.put("indexType", "SAI");
          
          // Generic SAI recommendation
          recommendations.put("reason", 
              "SAI (Storage Attached Index) provides high-performance indexing for Cassandra 5.0. "
              + "It supports equality queries, range queries, and for vector columns, "
              + "similarity search using ANN (Approximate Nearest Neighbor) algorithms.");
          
          // Suggest some common options
          Map<String, Object> suggestedOptions = new HashMap<>();
          suggestedOptions.put("similarity_function", 
              "For vector columns: 'cosine', 'euclidean', or 'dot_product'");
          suggestedOptions.put("case_sensitive", 
              "For text columns: true/false (default: false)");
          suggestedOptions.put("normalize", 
              "For text columns: true/false (default: true)");
          
          recommendations.put("availableOptions", suggestedOptions);
          analysis.put("recommendations", recommendations);
          
          return RestResponse.ok(analysis);
        });
  }
}