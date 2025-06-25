/*
 * Copyright DataStax, Inc. and/or The Stargate Authors
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
package io.stargate.sgv2.api.common.cql.builder;

import io.stargate.bridge.proto.QueryOuterClass.Query;
import io.stargate.bridge.proto.QueryOuterClass.Value;
import io.stargate.bridge.proto.QueryOuterClass.Values;
import io.stargate.sgv2.api.common.cql.CqlStrings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Specialized query builder for vector similarity search using Cassandra 5.0's ANN (Approximate
 * Nearest Neighbor) functionality.
 * 
 * <p>This builder creates queries with the ORDER BY ... ANN OF syntax:
 * <pre>
 * SELECT * FROM table 
 * WHERE filter_column = ? 
 * ORDER BY vector_column ANN OF ? 
 * LIMIT ?
 * </pre>
 */
public class VectorSearchQueryBuilder {
  
  private String keyspace;
  private String table;
  private final List<String> selectColumns = new ArrayList<>();
  private final List<WhereClause> whereFilters = new ArrayList<>();
  private String vectorColumn;
  private List<Float> vectorValue;
  private Integer limit;
  private final List<Value> boundValues = new ArrayList<>();
  
  private static class WhereClause {
    final String column;
    final String operator;
    final Object value;
    
    WhereClause(String column, String operator, Object value) {
      this.column = column;
      this.operator = operator;
      this.value = value;
    }
  }
  
  public VectorSearchQueryBuilder() {}
  
  public VectorSearchQueryBuilder select(String... columns) {
    for (String column : columns) {
      this.selectColumns.add(column);
    }
    return this;
  }
  
  public VectorSearchQueryBuilder selectAll() {
    this.selectColumns.clear(); // Clear any specific columns to use *
    return this;
  }
  
  public VectorSearchQueryBuilder from(String keyspace, String table) {
    this.keyspace = keyspace;
    this.table = table;
    return this;
  }
  
  public VectorSearchQueryBuilder from(String table) {
    this.table = table;
    return this;
  }
  
  public VectorSearchQueryBuilder where(String column, Object value) {
    return where(column, "=", value);
  }
  
  public VectorSearchQueryBuilder where(String column, String operator, Object value) {
    whereFilters.add(new WhereClause(column, operator, value));
    return this;
  }
  
  public VectorSearchQueryBuilder whereFilters(Map<String, Object> filters) {
    if (filters != null) {
      filters.forEach((column, value) -> where(column, "=", value));
    }
    return this;
  }
  
  public VectorSearchQueryBuilder orderByAnn(String vectorColumn, List<Float> vector) {
    this.vectorColumn = vectorColumn;
    this.vectorValue = vector;
    return this;
  }
  
  public VectorSearchQueryBuilder orderByAnn(String vectorColumn, float[] vector) {
    this.vectorColumn = vectorColumn;
    this.vectorValue = new ArrayList<>(vector.length);
    for (float v : vector) {
      this.vectorValue.add(v);
    }
    return this;
  }
  
  public VectorSearchQueryBuilder limit(int limit) {
    this.limit = limit;
    return this;
  }
  
  public Query build() {
    if (table == null) {
      throw new IllegalStateException("Table must be specified");
    }
    if (vectorColumn == null || vectorValue == null) {
      throw new IllegalStateException("Vector column and value must be specified for ANN search");
    }
    if (limit == null || limit <= 0) {
      throw new IllegalStateException("Limit must be specified and positive for ANN search");
    }
    
    StringBuilder cql = new StringBuilder();
    
    // SELECT clause
    cql.append("SELECT ");
    if (selectColumns.isEmpty()) {
      cql.append("*");
    } else {
      cql.append(selectColumns.stream()
          .map(CqlStrings::doubleQuote)
          .collect(Collectors.joining(", ")));
    }
    
    // FROM clause
    cql.append(" FROM ");
    if (keyspace != null) {
      cql.append(CqlStrings.doubleQuote(keyspace)).append(".");
    }
    cql.append(CqlStrings.doubleQuote(table));
    
    // WHERE clause (optional filters)
    if (!whereFilters.isEmpty()) {
      cql.append(" WHERE ");
      boolean first = true;
      for (WhereClause where : whereFilters) {
        if (!first) {
          cql.append(" AND ");
        }
        cql.append(CqlStrings.doubleQuote(where.column))
            .append(" ")
            .append(where.operator)
            .append(" ?");
        
        // Add the value to bound values
        boundValues.add(convertToValue(where.value));
        first = false;
      }
    }
    
    // ORDER BY ... ANN OF clause
    cql.append(" ORDER BY ")
        .append(CqlStrings.doubleQuote(vectorColumn))
        .append(" ANN OF ?");
    
    // Add vector value to bound values
    boundValues.add(io.stargate.bridge.grpc.Values.vector(vectorValue));
    
    // LIMIT clause
    cql.append(" LIMIT ").append(limit);
    
    // Build the Query
    Query.Builder queryBuilder = Query.newBuilder().setCql(cql.toString());
    
    if (!boundValues.isEmpty()) {
      queryBuilder.setValues(Values.newBuilder().addAllValues(boundValues).build());
    }
    
    return queryBuilder.build();
  }
  
  private Value convertToValue(Object value) {
    if (value instanceof String) {
      return io.stargate.bridge.grpc.Values.of((String) value);
    } else if (value instanceof Integer) {
      return io.stargate.bridge.grpc.Values.of((Integer) value);
    } else if (value instanceof Long) {
      return io.stargate.bridge.grpc.Values.of((Long) value);
    } else if (value instanceof Float) {
      return io.stargate.bridge.grpc.Values.of((Float) value);
    } else if (value instanceof Double) {
      return io.stargate.bridge.grpc.Values.of((Double) value);
    } else if (value instanceof Boolean) {
      return io.stargate.bridge.grpc.Values.of((Boolean) value);
    } else if (value instanceof java.util.UUID) {
      return io.stargate.bridge.grpc.Values.of((java.util.UUID) value);
    } else if (value instanceof List) {
      // Assume it's a list for collection types
      List<?> list = (List<?>) value;
      List<Value> values = new ArrayList<>(list.size());
      for (Object item : list) {
        values.add(convertToValue(item));
      }
      return io.stargate.bridge.grpc.Values.of(values);
    } else if (value instanceof Map) {
      // Handle map types
      Map<?, ?> map = (Map<?, ?>) value;
      Map<Value, Value> valueMap = new java.util.HashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        valueMap.put(convertToValue(entry.getKey()), convertToValue(entry.getValue()));
      }
      return io.stargate.bridge.grpc.Values.of(valueMap);
    } else {
      throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
    }
  }
}