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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.stargate.sgv2.docsapi.service.schema.query;

import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.sgv2.api.common.config.QueriesConfig;
import io.stargate.sgv2.api.common.cql.builder.Column;
import io.stargate.sgv2.api.common.cql.builder.QueryBuilder;
import io.stargate.sgv2.api.common.properties.datastore.DataStoreProperties;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentTableProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/** Provider of queries used to manage collections. */
@ApplicationScoped
public class CollectionQueryProvider {

  /** The constant for the storage attached index class. */
  public static final String STORAGE_ATTACHED_INDEX_CLASS = "StorageAttachedIndex";

  @Inject DocumentProperties documentProperties;

  @Inject DataStoreProperties dataStoreProperties;

  @Inject QueriesConfig queriesConfig;

  /**
   * Provides a query for creating a collection. Note that when creating a collection indexes should
   * be created separately using the {@link #createCollectionIndexQueries(String, String)}.
   *
   * @param namespace Namespace of the collection.
   * @param collection Collection name.
   * @return Query
   */
  public QueryOuterClass.Query createCollectionQuery(String namespace, String collection) {
    // all columns from the table props
    List<Column> columns = documentProperties.tableColumns().allColumns();

    // parameters for the local quorum
    QueryOuterClass.QueryParameters parameters = getQueryParameters();

    return new QueryBuilder()
        .create()
        .table(namespace, collection)
        .column(columns)
        .parameters(parameters)
        .build();
  }

  /**
   * Provides query for deleting the collection.
   *
   * @param namespace Namespace of the collection.
   * @param collection Collection name.
   * @return Query
   */
  public QueryOuterClass.Query deleteCollectionQuery(String namespace, String collection) {
    // parameters for the local quorum
    QueryOuterClass.QueryParameters parameters = getQueryParameters();

    // construct delete query
    return new QueryBuilder().drop().table(namespace, collection).parameters(parameters).build();
  }

  /**
   * Creates a set of queries to be performed for creating all needed indexes for a collection.
   *
   * @param namespace Namespace of the collection.
   * @param collection Collection name.
   * @return Query
   */
  public List<QueryOuterClass.Query> createCollectionIndexQueries(
      String namespace, String collection) {
    DocumentTableProperties tableProperties = documentProperties.tableProperties();
    List<QueryOuterClass.Query> indexQueries = new ArrayList<>();

    if (dataStoreProperties.saiEnabled()) {
      indexQueries.add(
          createIndexQuery(
              namespace,
              collection,
              tableProperties.leafColumnName(),
              STORAGE_ATTACHED_INDEX_CLASS));
      indexQueries.add(
          createIndexQuery(
              namespace,
              collection,
              tableProperties.stringValueColumnName(),
              STORAGE_ATTACHED_INDEX_CLASS));
      indexQueries.add(
          createIndexQuery(
              namespace,
              collection,
              tableProperties.doubleValueColumnName(),
              STORAGE_ATTACHED_INDEX_CLASS));
      // note that SAI doesn't support booleans, but we are using numeric booleans
      indexQueries.add(
          createIndexQuery(
              namespace,
              collection,
              tableProperties.booleanValueColumnName(),
              STORAGE_ATTACHED_INDEX_CLASS));
      // Create vector index with cosine similarity for ANN search
      // Note: This requires Cassandra 5.0 with SAI support for vector types
      indexQueries.add(
          createVectorIndexQuery(
              namespace, collection, tableProperties.vectorValueColumnName(), "cosine"));
    } else {
      indexQueries.add(
          createIndexQuery(namespace, collection, tableProperties.leafColumnName(), null));
      indexQueries.add(
          createIndexQuery(namespace, collection, tableProperties.stringValueColumnName(), null));
      indexQueries.add(
          createIndexQuery(namespace, collection, tableProperties.doubleValueColumnName(), null));
      indexQueries.add(
          createIndexQuery(namespace, collection, tableProperties.booleanValueColumnName(), null));
      // Skip vector index for non-SAI deployments as it requires SAI for ANN search
    }

    return indexQueries;
  }

  private QueryOuterClass.Query createIndexQuery(
      String keyspaceName, String tableName, String indexedColumn, String customIndexClass) {
    QueryOuterClass.QueryParameters parameters = getQueryParameters();

    return new QueryBuilder()
        .create()
        .index()
        .ifNotExists()
        .on(keyspaceName, tableName)
        .column(indexedColumn)
        .custom(customIndexClass)
        .parameters(parameters)
        .build();
  }

  private QueryOuterClass.Query createVectorIndexQuery(
      String keyspaceName, String tableName, String vectorColumn, String similarityFunction) {
    QueryOuterClass.QueryParameters parameters = getQueryParameters();

    // For Cassandra 5.0 SAI vector indexes, we need to create an index with similarity function
    // The syntax is: CREATE CUSTOM INDEX ON table(column) USING 'StorageAttachedIndex'
    // WITH OPTIONS = {'similarity_function': 'cosine'}
    String indexName = "idx_" + tableName + "_" + vectorColumn;
    String cql =
        String.format(
            "CREATE CUSTOM INDEX IF NOT EXISTS %s ON %s.%s(%s) USING '%s' WITH OPTIONS = {'similarity_function': '%s'}",
            indexName,
            keyspaceName,
            tableName,
            vectorColumn,
            STORAGE_ATTACHED_INDEX_CLASS,
            similarityFunction);

    return QueryOuterClass.Query.newBuilder().setCql(cql).setParameters(parameters).build();
  }

  // constructs parameters for the queries in this provider
  private QueryOuterClass.QueryParameters getQueryParameters() {
    QueryOuterClass.Consistency consistency = queriesConfig.consistency().schemaChanges();

    return QueryOuterClass.QueryParameters.newBuilder()
        .setConsistency(QueryOuterClass.ConsistencyValue.newBuilder().setValue(consistency))
        .build();
  }
}
