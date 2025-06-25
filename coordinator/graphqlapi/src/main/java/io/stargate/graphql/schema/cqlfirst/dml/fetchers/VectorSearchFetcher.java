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
package io.stargate.graphql.schema.cqlfirst.dml.fetchers;

import graphql.schema.DataFetchingEnvironment;
import io.stargate.auth.SourceAPI;
import io.stargate.auth.TypedKeyValue;
import io.stargate.core.util.ByteBufferUtils;
import io.stargate.db.datastore.DataStore;
import io.stargate.db.datastore.ResultSet;
import io.stargate.db.query.BoundQuery;
import io.stargate.db.query.BoundSelect;
import io.stargate.db.query.Predicate;
import io.stargate.db.query.builder.BuiltCondition;
import io.stargate.db.schema.Column;
import io.stargate.db.schema.Table;
import io.stargate.graphql.schema.cqlfirst.dml.NameMapping;
import io.stargate.graphql.web.StargateGraphqlContext;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fetcher for vector similarity search operations using Cassandra 5.0's ANN functionality. This
 * fetcher handles GraphQL queries for finding similar vectors.
 */
public class VectorSearchFetcher extends DmlFetcher<Map<String, Object>> {

  public VectorSearchFetcher(Table table, NameMapping nameMapping) {
    super(table, nameMapping);
  }

  @Override
  protected Map<String, Object> get(
      DataFetchingEnvironment environment, StargateGraphqlContext context) throws Exception {

    // Extract arguments
    String vectorColumn = environment.getArgument("vectorColumn");
    List<Float> queryVector = environment.getArgument("vector");
    Integer limit = environment.getArgument("limit");
    Map<String, Object> filters = environment.getArgument("filter");

    // Validate required arguments
    if (vectorColumn == null || vectorColumn.isEmpty()) {
      throw new IllegalArgumentException("vectorColumn is required for vector search");
    }
    if (queryVector == null || queryVector.isEmpty()) {
      throw new IllegalArgumentException("vector is required for vector search");
    }
    if (limit == null || limit <= 0) {
      limit = 10; // Default limit
    }

    // Build the ANN query
    BoundQuery query =
        buildVectorSearchQuery(
            environment, context.getDataStore(), vectorColumn, queryVector, limit, filters);

    // Execute the query with authorization
    ResultSet resultSet =
        context
            .getAuthorizationService()
            .authorizedDataRead(
                () ->
                    context.getDataStore().execute(query, __ -> buildParameters(environment)).get(),
                context.getSubject(),
                table.keyspace(),
                table.name(),
                TypedKeyValue.forSelect((BoundSelect) query),
                SourceAPI.GRAPHQL);

    // Build the result
    Map<String, Object> result = new HashMap<>();
    result.put(
        "values",
        resultSet.currentPageRows().stream()
            .map(row -> DataTypeMapping.toGraphQLValue(nameMapping, table, row))
            .collect(Collectors.toList()));

    ByteBuffer pageState = resultSet.getPagingState();
    if (pageState != null) {
      result.put("pageState", ByteBufferUtils.toBase64(pageState));
    }

    return result;
  }

  private BoundQuery buildVectorSearchQuery(
      DataFetchingEnvironment environment,
      DataStore dataStore,
      String vectorColumn,
      List<Float> queryVector,
      Integer limit,
      Map<String, Object> filters) {

    // Get the actual database column name
    String dbVectorColumn = dbColumnGetter.getDBColumnName(table, vectorColumn);
    if (dbVectorColumn == null) {
      throw new IllegalArgumentException("Unknown vector column: " + vectorColumn);
    }

    // Build the query - note that the current query builder doesn't support ANN syntax
    // This is a simplified implementation that will need to be updated once the
    // query builder supports ORDER BY ... ANN OF syntax

    // TODO: The query builder needs to support ORDER BY ... ANN OF syntax
    // This is a placeholder implementation that won't actually perform ANN search
    // until the query builder is updated to support it

    return dataStore
        .queryBuilder()
        .select()
        .column(buildQueryColumns(environment))
        .from(table.keyspace(), table.name())
        .where(buildClause(table, environment))
        .limit(limit)
        .build()
        .bind();
  }

  private List<Column> buildQueryColumns(DataFetchingEnvironment environment) {
    // Similar to QueryFetcher, build list of columns to select
    List<Column> columns = new ArrayList<>();

    // For now, select all columns (can be optimized later based on selection set)
    for (Column column : table.columns()) {
      columns.add(column);
    }

    return columns;
  }

  private List<BuiltCondition> buildFilterClause(Map<String, Object> filters) {
    List<BuiltCondition> conditions = new ArrayList<>();

    for (Map.Entry<String, Object> entry : filters.entrySet()) {
      String columnName = dbColumnGetter.getDBColumnName(table, entry.getKey());
      if (columnName != null) {
        conditions.add(BuiltCondition.of(columnName, Predicate.EQ, entry.getValue()));
      }
    }

    return conditions;
  }
}
