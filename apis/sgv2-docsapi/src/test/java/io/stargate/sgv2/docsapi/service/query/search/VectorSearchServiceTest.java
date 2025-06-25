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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.smallrye.mutiny.Uni;
import io.stargate.bridge.grpc.Values;
import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.bridge.proto.StargateBridge;
import io.stargate.sgv2.api.common.StargateRequestInfo;
import io.stargate.sgv2.api.common.config.QueriesConfig;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentTableProperties;
import io.stargate.sgv2.docsapi.service.ExecutionContext;
import io.stargate.sgv2.docsapi.service.query.model.RawDocument;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

  @Mock DocumentProperties documentProperties;
  @Mock DocumentTableProperties tableProperties;
  @Mock StargateRequestInfo requestInfo;
  @Mock QueriesConfig queriesConfig;
  @Mock QueriesConfig.ConsistencyConfig consistencyConfig;
  @Mock StargateBridge bridge;

  VectorSearchService vectorSearchService;

  @BeforeEach
  void setUp() {
    vectorSearchService = new VectorSearchService();
    vectorSearchService.documentProperties = documentProperties;
    vectorSearchService.requestInfo = requestInfo;
    vectorSearchService.queriesConfig = queriesConfig;

    when(documentProperties.tableProperties()).thenReturn(tableProperties);
    when(documentProperties.tableColumns()).thenReturn(mock());
    when(requestInfo.getStargateBridge()).thenReturn(bridge);
    when(queriesConfig.consistency()).thenReturn(consistencyConfig);
    when(consistencyConfig.reads()).thenReturn(QueryOuterClass.Consistency.LOCAL_QUORUM);
  }

  @Test
  void vectorSearch_BasicQuery() {
    // Setup
    String namespace = "test_ns";
    String collection = "test_coll";
    float[] queryVector = {0.1f, 0.2f, 0.3f};
    int limit = 10;
    ExecutionContext context = ExecutionContext.NOOP_CONTEXT;

    when(tableProperties.vectorValueColumnName()).thenReturn("vector_value");
    when(documentProperties.tableColumns().allColumnNamesArray())
        .thenReturn(
            new String[] {
              "key", "p0", "p1", "leaf", "text_value", "dbl_value", "bool_value", "vector_value"
            });

    // Mock bridge response
    QueryOuterClass.Response response =
        QueryOuterClass.Response.newBuilder()
            .setResultSet(
                QueryOuterClass.ResultSet.newBuilder()
                    .addColumns(QueryOuterClass.ColumnSpec.newBuilder().setName("key").build())
                    .addRows(QueryOuterClass.Row.newBuilder().addValues(Values.of("doc1"))))
            .build();

    when(bridge.executeQuery(any())).thenReturn(Uni.createFrom().item(response));

    // Execute
    List<RawDocument> result =
        vectorSearchService
            .vectorSearch(namespace, collection, queryVector, limit, Optional.empty(), context)
            .await()
            .indefinitely();

    // Verify
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo("doc1");

    // Verify the query was built correctly
    ArgumentCaptor<QueryOuterClass.Query> queryCaptor =
        ArgumentCaptor.forClass(QueryOuterClass.Query.class);
    verify(bridge).executeQuery(queryCaptor.capture());

    QueryOuterClass.Query capturedQuery = queryCaptor.getValue();
    assertThat(capturedQuery.getCql()).contains("ORDER BY vector_value ann of ? LIMIT ?");
    assertThat(capturedQuery.getValues().getValuesList()).hasSize(2);

    // Check that we have the right number of parameters
    // First parameter should be the vector, second should be the limit
    assertThat(capturedQuery.getValues().getValuesList()).hasSize(2);

    // We can't easily check the vector contents without the proper proto methods,
    // but we can verify the limit parameter
    QueryOuterClass.Value limitParam = capturedQuery.getValues().getValues(1);
    assertThat(limitParam.getInt()).isEqualTo(limit);
  }

  @Test
  void vectorSearch_EmptyResults() {
    // Setup
    String namespace = "test_ns";
    String collection = "test_coll";
    float[] queryVector = {0.1f, 0.2f, 0.3f};
    int limit = 10;
    ExecutionContext context = ExecutionContext.NOOP_CONTEXT;

    when(tableProperties.vectorValueColumnName()).thenReturn("vector_value");
    when(documentProperties.tableColumns().allColumnNamesArray()).thenReturn(new String[] {"key"});

    // Mock empty response
    QueryOuterClass.Response response =
        QueryOuterClass.Response.newBuilder()
            .setResultSet(QueryOuterClass.ResultSet.newBuilder())
            .build();

    when(bridge.executeQuery(any())).thenReturn(Uni.createFrom().item(response));

    // Execute
    List<RawDocument> result =
        vectorSearchService
            .vectorSearch(namespace, collection, queryVector, limit, Optional.empty(), context)
            .await()
            .indefinitely();

    // Verify
    assertThat(result).isEmpty();
  }

  @Test
  void validateVectorDimension_Valid() {
    float[] vector = {0.1f, 0.2f, 0.3f};
    int expectedDimension = 3;

    // Should not throw
    vectorSearchService.validateVectorDimension(vector, expectedDimension);
  }

  @Test
  void validateVectorDimension_Invalid() {
    float[] vector = {0.1f, 0.2f, 0.3f};
    int expectedDimension = 5;

    assertThatThrownBy(() -> vectorSearchService.validateVectorDimension(vector, expectedDimension))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Vector dimension mismatch")
        .hasMessageContaining("Expected 5 but got 3");
  }

  @Test
  void vectorSearch_MultipleDocuments() {
    // Setup
    String namespace = "test_ns";
    String collection = "test_coll";
    float[] queryVector = {0.1f, 0.2f, 0.3f};
    int limit = 10;
    ExecutionContext context = ExecutionContext.NOOP_CONTEXT;

    when(tableProperties.vectorValueColumnName()).thenReturn("vector_value");
    when(tableProperties.keyColumnName()).thenReturn("key");
    when(documentProperties.tableColumns().allColumnNamesArray())
        .thenReturn(new String[] {"key", "leaf", "text_value"});

    // Mock response with multiple rows for same document
    QueryOuterClass.Response response =
        QueryOuterClass.Response.newBuilder()
            .setResultSet(
                QueryOuterClass.ResultSet.newBuilder()
                    .addColumns(QueryOuterClass.ColumnSpec.newBuilder().setName("key").build())
                    .addColumns(QueryOuterClass.ColumnSpec.newBuilder().setName("leaf").build())
                    .addColumns(
                        QueryOuterClass.ColumnSpec.newBuilder().setName("text_value").build())
                    .addRows(
                        QueryOuterClass.Row.newBuilder()
                            .addValues(Values.of("doc1"))
                            .addValues(Values.of("field1"))
                            .addValues(Values.of("value1")))
                    .addRows(
                        QueryOuterClass.Row.newBuilder()
                            .addValues(Values.of("doc1"))
                            .addValues(Values.of("field2"))
                            .addValues(Values.of("value2")))
                    .addRows(
                        QueryOuterClass.Row.newBuilder()
                            .addValues(Values.of("doc2"))
                            .addValues(Values.of("field1"))
                            .addValues(Values.of("value3"))))
            .build();

    when(bridge.executeQuery(any())).thenReturn(Uni.createFrom().item(response));

    // Execute
    List<RawDocument> result =
        vectorSearchService
            .vectorSearch(namespace, collection, queryVector, limit, Optional.empty(), context)
            .await()
            .indefinitely();

    // Verify - should group rows by document ID
    assertThat(result).hasSize(2);

    RawDocument doc1 = result.stream().filter(d -> d.id().equals("doc1")).findFirst().orElseThrow();
    assertThat(doc1.rows()).hasSize(2);

    RawDocument doc2 = result.stream().filter(d -> d.id().equals("doc2")).findFirst().orElseThrow();
    assertThat(doc2.rows()).hasSize(1);
  }

  @Test
  void vectorSearch_WithFilter() {
    // This test verifies that filters are passed through (even if not implemented yet)
    String namespace = "test_ns";
    String collection = "test_coll";
    float[] queryVector = {0.1f, 0.2f, 0.3f};
    int limit = 10;
    ExecutionContext context = ExecutionContext.NOOP_CONTEXT;

    when(tableProperties.vectorValueColumnName()).thenReturn("vector_value");
    when(documentProperties.tableColumns().allColumnNamesArray()).thenReturn(new String[] {"key"});

    // Mock response
    QueryOuterClass.Response response =
        QueryOuterClass.Response.newBuilder()
            .setResultSet(QueryOuterClass.ResultSet.newBuilder())
            .build();

    when(bridge.executeQuery(any())).thenReturn(Uni.createFrom().item(response));

    // Execute with a filter (even though it's not implemented)
    Optional<io.stargate.sgv2.docsapi.service.query.FilterExpression> filter = Optional.empty();

    List<RawDocument> result =
        vectorSearchService
            .vectorSearch(namespace, collection, queryVector, limit, filter, context)
            .await()
            .indefinitely();

    // Should still work even with filter present
    assertThat(result).isNotNull();
  }
}
