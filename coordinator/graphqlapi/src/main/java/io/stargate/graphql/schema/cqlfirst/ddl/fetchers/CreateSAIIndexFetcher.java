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
package io.stargate.graphql.schema.cqlfirst.ddl.fetchers;

import graphql.schema.DataFetchingEnvironment;
import io.stargate.auth.Scope;
import io.stargate.db.query.Query;
import io.stargate.db.query.builder.QueryBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * Specialized fetcher for creating Storage Attached Indexes (SAI) in Cassandra 5.0. Provides a
 * simplified interface with SAI-specific options.
 */
public class CreateSAIIndexFetcher extends IndexFetcher {

  private static final String SAI_INDEX_CLASS =
      "org.apache.cassandra.index.sai.StorageAttachedIndex";

  public CreateSAIIndexFetcher() {
    super(Scope.CREATE);
  }

  @Override
  protected Query<?> buildQuery(
      DataFetchingEnvironment environment,
      QueryBuilder builder,
      String keyspaceName,
      String tableName) {

    String columnName = environment.getArgument("columnName");
    String indexName = environment.getArgument("indexName");
    boolean ifNotExists = environment.getArgumentOrDefault("ifNotExists", Boolean.FALSE);

    // SAI-specific options
    Map<String, String> options = new HashMap<>();

    // Text analysis options
    Boolean normalize = environment.getArgument("normalize");
    if (normalize != null) {
      options.put("normalize", normalize.toString());
    }

    Boolean caseSensitive = environment.getArgument("caseSensitive");
    if (caseSensitive != null) {
      options.put("case_sensitive", caseSensitive.toString());
    }

    Boolean asciiOnly = environment.getArgument("asciiOnly");
    if (asciiOnly != null) {
      options.put("ascii", asciiOnly.toString());
    }

    String analyzerClass = environment.getArgument("analyzerClass");
    if (analyzerClass != null) {
      options.put("analyzer_class", analyzerClass);
    }

    // Vector similarity function
    String similarityFunction = environment.getArgument("similarityFunction");
    if (similarityFunction != null) {
      options.put("similarity_function", similarityFunction);
    }

    return builder
        .create()
        .index(indexName)
        .ifNotExists(ifNotExists)
        .on(keyspaceName, tableName)
        .column(columnName)
        .custom(SAI_INDEX_CLASS, options.isEmpty() ? null : options)
        .build();
  }
}
