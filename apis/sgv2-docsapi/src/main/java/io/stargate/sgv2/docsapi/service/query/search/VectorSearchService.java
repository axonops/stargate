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
package io.stargate.sgv2.docsapi.service.query.search;

import io.smallrye.mutiny.Uni;
import io.stargate.bridge.grpc.Values;
import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.sgv2.api.common.StargateRequestInfo;
import io.stargate.sgv2.api.common.config.QueriesConfig;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentTableProperties;
import io.stargate.sgv2.docsapi.service.ExecutionContext;
import io.stargate.sgv2.docsapi.service.common.model.RowWrapper;
import io.stargate.sgv2.docsapi.service.query.FilterExpression;
import io.stargate.sgv2.docsapi.service.query.model.ImmutableRawDocument;
import io.stargate.sgv2.docsapi.service.query.model.RawDocument;
import io.stargate.sgv2.docsapi.service.query.model.paging.CombinedPagingState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service responsible for executing vector similarity searches against document collections. */
@ApplicationScoped
public class VectorSearchService {
  private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

  @Inject DocumentProperties documentProperties;
  @Inject StargateRequestInfo requestInfo;
  @Inject QueriesConfig queriesConfig;

  /**
   * Performs a vector similarity search on a collection.
   *
   * @param namespace The keyspace/namespace containing the collection
   * @param collection The collection name to search in
   * @param queryVector The query vector to search for similar vectors
   * @param limit Maximum number of results to return
   * @param additionalFilter Optional additional filter to apply to results
   * @param context Execution context
   * @return List of documents that match the similarity search
   */
  public Uni<List<RawDocument>> vectorSearch(
      String namespace,
      String collection,
      float[] queryVector,
      int limit,
      Optional<FilterExpression> additionalFilter,
      ExecutionContext context) {

    String keyspace = namespace;
    String table = collection;
    DocumentTableProperties tableProps = documentProperties.tableProperties();

    // Build the base query with ORDER BY ... ann of
    // Note: QueryBuilder doesn't support ANN queries directly, so we'll build the CQL manually

    // Add ANN ordering for vector similarity
    // Note: We need to use a custom query format since QueryBuilder doesn't have direct ANN support
    // The query pattern is: SELECT ... FROM ... ORDER BY vector_column ann of ? LIMIT ?

    // Add additional filters if provided
    if (additionalFilter.isPresent()) {
      // TODO: Convert FilterExpression to BuiltConditions
      // This would require extending the filter expression framework to support vector queries
      logger.warn("Additional filters for vector search not yet implemented");
    }

    // For now, build a simple ANN query without additional filters
    String cql =
        String.format(
            "SELECT %s FROM %s.%s ORDER BY %s ann of ? LIMIT ?",
            String.join(", ", documentProperties.tableColumns().allColumnNamesArray()),
            keyspace,
            table,
            tableProps.vectorValueColumnName());

    // Create values for the query
    List<QueryOuterClass.Value> values = new ArrayList<>();
    values.add(Values.vector(queryVector));
    values.add(Values.of(limit));

    QueryOuterClass.Query query =
        QueryOuterClass.Query.newBuilder()
            .setCql(cql)
            .setValues(QueryOuterClass.Values.newBuilder().addAllValues(values))
            .build();

    // Add consistency level
    QueryOuterClass.Consistency consistency = queriesConfig.consistency().reads();
    QueryOuterClass.ConsistencyValue consistencyValue =
        QueryOuterClass.ConsistencyValue.newBuilder().setValue(consistency).build();

    QueryOuterClass.QueryParameters params =
        QueryOuterClass.QueryParameters.newBuilder().setConsistency(consistencyValue).build();

    QueryOuterClass.Query finalQuery = query.toBuilder().setParameters(params).build();

    // Execute the query using StargateBridge
    return requestInfo
        .getStargateBridge()
        .executeQuery(finalQuery)
        .onItem()
        .transform(
            response -> {
              List<RawDocument> documents = new ArrayList<>();
              QueryOuterClass.ResultSet resultSet = response.getResultSet();

              // Create row wrapper function
              Function<QueryOuterClass.Row, RowWrapper> wrapperFunction =
                  RowWrapper.forColumns(resultSet.getColumnsList());

              // Group rows by document ID
              Map<String, List<RowWrapper>> rowsByDocId = new HashMap<>();
              for (QueryOuterClass.Row row : resultSet.getRowsList()) {
                RowWrapper rowWrapper = wrapperFunction.apply(row);
                String docId =
                    rowWrapper.getString(documentProperties.tableProperties().keyColumnName());
                rowsByDocId.computeIfAbsent(docId, k -> new ArrayList<>()).add(rowWrapper);
              }

              // Create RawDocument for each document ID
              for (Map.Entry<String, List<RowWrapper>> entry : rowsByDocId.entrySet()) {
                String docId = entry.getKey();
                List<RowWrapper> docRows = entry.getValue();
                RawDocument document =
                    ImmutableRawDocument.of(
                        docId,
                        Collections.singletonList(docId),
                        new CombinedPagingState(Collections.emptyList()),
                        docRows);
                documents.add(document);
              }

              // Log CQL execution
              context.traceCqlResult(cql, documents.size());

              return documents;
            });
  }

  /**
   * Validates that a vector has the expected dimension.
   *
   * @param vector The vector to validate
   * @param expectedDimension The expected dimension
   * @throws IllegalArgumentException if the vector dimension doesn't match
   */
  public void validateVectorDimension(float[] vector, int expectedDimension) {
    if (vector.length != expectedDimension) {
      throw new IllegalArgumentException(
          String.format(
              "Vector dimension mismatch. Expected %d but got %d",
              expectedDimension, vector.length));
    }
  }
}
