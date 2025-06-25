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
package io.stargate.db.query.builder;

import io.stargate.db.query.AsyncQueryExecutor;
import io.stargate.db.query.TypedValue.Codec;
import io.stargate.db.schema.Schema;

/**
 * Query builder that provides a fluent DSL interface.
 *
 * <p>This replaces the duzzt-generated DSL with a simple builder pattern. It wraps QueryBuilderImpl
 * and provides a fluent interface.
 */
public class QueryBuilder {
  private final QueryBuilderImpl impl;

  public QueryBuilder(Schema schema, Codec valueCodec, AsyncQueryExecutor executor) {
    this.impl = new QueryBuilderImpl(schema, valueCodec, executor);
  }

  public QueryBuilder(Schema schema, Codec valueCodec) {
    this.impl = new QueryBuilderImpl(schema, valueCodec, null);
  }

  // Fluent methods
  public QueryBuilder select() {
    impl.select();
    return this;
  }

  public QueryBuilder column(String... columns) {
    for (String col : columns) {
      impl.column(col);
    }
    return this;
  }

  public QueryBuilder from(String keyspace, String table) {
    impl.from(keyspace, table);
    return this;
  }

  public QueryBuilder from(io.stargate.db.schema.Table table) {
    impl.from(table);
    return this;
  }

  public QueryBuilder where(
      String columnName, io.stargate.db.query.Predicate predicate, Object value) {
    impl.where(columnName, predicate, value);
    return this;
  }

  public QueryBuilder where(String columnName, io.stargate.db.query.Predicate predicate) {
    impl.where(columnName, predicate);
    return this;
  }

  public QueryBuilder where(
      io.stargate.db.schema.Column column, io.stargate.db.query.Predicate predicate, Object value) {
    impl.where(column, predicate, value);
    return this;
  }

  public BuiltQuery<?> build() {
    return impl.build();
  }

  // Add more fluent methods as needed

  // CREATE operations
  public QueryBuilder create() {
    impl.create();
    return this;
  }

  public QueryBuilder table(String keyspace, String table) {
    impl.table(keyspace, table);
    return this;
  }

  public QueryBuilder table(String table) {
    impl.table(table);
    return this;
  }

  public QueryBuilder column(String column, io.stargate.db.schema.Column.Type type) {
    impl.column(column, type);
    return this;
  }

  public QueryBuilder column(String column, io.stargate.db.schema.Column.ColumnType type) {
    impl.column(column, type);
    return this;
  }

  public QueryBuilder column(
      String column,
      io.stargate.db.schema.Column.ColumnType type,
      io.stargate.db.schema.Column.Kind kind) {
    impl.column(column, type, kind);
    return this;
  }

  public QueryBuilder column(
      String column,
      io.stargate.db.schema.Column.Type type,
      io.stargate.db.schema.Column.Kind kind) {
    impl.column(column, type, kind);
    return this;
  }

  public QueryBuilder column(java.util.Collection<io.stargate.db.schema.Column> columns) {
    impl.column(columns);
    return this;
  }

  public QueryBuilder column(io.stargate.db.schema.Column column) {
    impl.column(column);
    return this;
  }

  public QueryBuilder column(String column, io.stargate.db.schema.Column.Kind kind) {
    impl.column(column, kind);
    return this;
  }

  // Overloaded column method that accepts a List of columns
  public QueryBuilder column(java.util.List<io.stargate.db.schema.Column> columns) {
    impl.column(columns);
    return this;
  }

  public QueryBuilder column(
      String column,
      io.stargate.db.schema.Column.Kind kind,
      io.stargate.db.schema.Column.Order order) {
    impl.column(column, kind, order);
    return this;
  }

  // INSERT operations
  public QueryBuilder insertInto(String keyspace, String table) {
    impl.insertInto(keyspace, table);
    return this;
  }

  public QueryBuilder insertInto(io.stargate.db.schema.Table table) {
    impl.insertInto(table);
    return this;
  }

  public QueryBuilder value(String column, Object value) {
    impl.value(column, value);
    return this;
  }

  public QueryBuilder value(io.stargate.db.schema.Column column, Object value) {
    impl.value(column, value);
    return this;
  }

  // UPDATE operations
  public QueryBuilder update(String keyspace, String table) {
    impl.update(keyspace, table);
    return this;
  }

  public QueryBuilder update(io.stargate.db.schema.Table table) {
    impl.update(table);
    return this;
  }

  public QueryBuilder ttl(int ttl) {
    impl.ttl(ttl);
    return this;
  }

  // SELECT options
  public QueryBuilder limit() {
    impl.limit();
    return this;
  }

  public QueryBuilder limit(Integer limit) {
    impl.limit(limit);
    return this;
  }

  public QueryBuilder perPartitionLimit() {
    impl.perPartitionLimit();
    return this;
  }

  public QueryBuilder perPartitionLimit(Integer limit) {
    impl.perPartitionLimit(limit);
    return this;
  }

  // DELETE operations
  public QueryBuilder delete() {
    impl.delete();
    return this;
  }

  // Note: delete operations use delete() + from() methods, not deleteFrom()

  // Other operations
  public QueryBuilder ifNotExists() {
    impl.ifNotExists();
    return this;
  }

  public QueryBuilder ifExists() {
    impl.ifExists();
    return this;
  }

  public QueryBuilder withDefaultTTL(int defaultTTL) {
    impl.withDefaultTTL(defaultTTL);
    return this;
  }

  public QueryBuilder orderBy(String column, io.stargate.db.schema.Column.Order order) {
    impl.orderBy(column, order);
    return this;
  }

  // Overloaded orderBy method that accepts a list of ColumnOrder
  public QueryBuilder orderBy(java.util.List<io.stargate.db.query.builder.ColumnOrder> orders) {
    impl.orderBy(orders);
    return this;
  }

  // Column with clustering order (for CREATE TABLE)
  public QueryBuilder column(
      String column,
      io.stargate.db.schema.Column.Type type,
      io.stargate.db.schema.Column.Kind kind,
      io.stargate.db.schema.Column.Order order) {
    impl.column(column, type, kind, order);
    return this;
  }

  // SELECT * operations
  public QueryBuilder star() {
    impl.star();
    return this;
  }

  // Write time column selection
  public QueryBuilder writeTimeColumn(String columnName) {
    impl.writeTimeColumn(columnName);
    return this;
  }

  // Function operations (for aggregations)
  public QueryBuilder function(java.util.Collection<QueryBuilderImpl.FunctionCall> functions) {
    impl.function(functions);
    return this;
  }

  // Count operations
  public QueryBuilder count(String columnName) {
    impl.count(columnName);
    return this;
  }

  public QueryBuilder count(io.stargate.db.schema.Column column) {
    impl.count(column);
    return this;
  }

  // Max operations
  public QueryBuilder max(String columnName) {
    impl.max(columnName);
    return this;
  }

  public QueryBuilder max(io.stargate.db.schema.Column column) {
    impl.max(column);
    return this;
  }

  // Min operations
  public QueryBuilder min(String columnName) {
    impl.min(columnName);
    return this;
  }

  public QueryBuilder min(io.stargate.db.schema.Column column) {
    impl.min(column);
    return this;
  }

  // Sum operations
  public QueryBuilder sum(String columnName) {
    impl.sum(columnName);
    return this;
  }

  public QueryBuilder sum(io.stargate.db.schema.Column column) {
    impl.sum(column);
    return this;
  }

  // Avg operations
  public QueryBuilder avg(String columnName) {
    impl.avg(columnName);
    return this;
  }

  public QueryBuilder avg(io.stargate.db.schema.Column column) {
    impl.avg(column);
    return this;
  }

  // Where with list of conditions
  public QueryBuilder where(java.util.List<BuiltCondition> conditions) {
    impl.where(conditions);
    return this;
  }

  // Where with single condition
  public QueryBuilder where(BuiltCondition condition) {
    impl.where(condition);
    return this;
  }

  // Group by operations
  public QueryBuilder groupBy(java.util.List<io.stargate.db.schema.Column> columns) {
    impl.groupBy(columns);
    return this;
  }

  // Overloaded groupBy method that accepts varargs
  public QueryBuilder groupBy(io.stargate.db.schema.Column... columns) {
    impl.groupBy(columns);
    return this;
  }

  // DROP operations
  public QueryBuilder drop() {
    impl.drop();
    return this;
  }

  // Drop column methods (for ALTER TABLE)
  public QueryBuilder dropColumn(String column) {
    impl.dropColumn(column);
    return this;
  }

  public QueryBuilder dropColumn(java.util.Collection<String> columns) {
    impl.dropColumn(columns);
    return this;
  }

  public QueryBuilder dropColumn(io.stargate.db.schema.Column column) {
    impl.dropColumn(column);
    return this;
  }

  // ALTER operations
  public QueryBuilder alter() {
    impl.alter();
    return this;
  }

  public QueryBuilder addColumn(String column, io.stargate.db.schema.Column.Type type) {
    impl.addColumn(column, type);
    return this;
  }

  // Overloaded addColumn for adding a Column object
  public QueryBuilder addColumn(io.stargate.db.schema.Column column) {
    impl.addColumn(column);
    return this;
  }

  // Overloaded addColumn for adding multiple columns
  public QueryBuilder addColumn(java.util.Collection<io.stargate.db.schema.Column> columns) {
    impl.addColumn(columns);
    return this;
  }

  public QueryBuilder renameColumn(String oldName, String newName) {
    impl.renameColumn(oldName, newName);
    return this;
  }

  // CREATE KEYSPACE operations
  public QueryBuilder keyspace(String keyspace) {
    impl.keyspace(keyspace);
    return this;
  }

  public QueryBuilder withReplication(io.stargate.db.query.builder.Replication replication) {
    impl.withReplication(replication);
    return this;
  }

  // Table options
  public QueryBuilder withComment(String comment) {
    impl.withComment(comment);
    return this;
  }

  // Value method with column name only (for prepared statements)
  public QueryBuilder value(String column) {
    impl.value(column);
    return this;
  }

  // Value method with list of ValueModifiers
  public QueryBuilder value(java.util.List<ValueModifier> modifiers) {
    impl.value(modifiers);
    return this;
  }

  // Note: Collection operations (append, prepend, remove, put) and function operations
  // are handled through ValueModifier in the implementation.

  // Overloaded ifExists method that accepts a boolean parameter
  public QueryBuilder ifExists(boolean ifExists) {
    impl.ifExists(ifExists);
    return this;
  }

  // Overloaded ifNotExists method that accepts a boolean parameter
  public QueryBuilder ifNotExists(boolean ifNotExists) {
    impl.ifNotExists(ifNotExists);
    return this;
  }

  // CREATE TYPE operations
  // Note: type() method only accepts UserDefinedType, not String

  public QueryBuilder type(String keyspace, io.stargate.db.schema.UserDefinedType type) {
    impl.type(keyspace, type);
    return this;
  }

  // Materialized view operations
  public QueryBuilder materializedView(String keyspace, String viewName) {
    impl.materializedView(keyspace, viewName);
    return this;
  }

  public QueryBuilder as(String alias) {
    impl.as(alias);
    return this;
  }

  public QueryBuilder asSelect() {
    impl.asSelect();
    return this;
  }

  // Keyspace options
  public QueryBuilder andDurableWrites(boolean durableWrites) {
    impl.andDurableWrites(durableWrites);
    return this;
  }

  // Index operations
  public QueryBuilder index(String indexName) {
    impl.index(indexName);
    return this;
  }

  // Overloaded index method with keyspace and index name
  public QueryBuilder index(String keyspace, String indexName) {
    impl.index(keyspace, indexName);
    return this;
  }

  public QueryBuilder indexKeys() {
    impl.indexKeys();
    return this;
  }

  public QueryBuilder indexValues() {
    impl.indexValues();
    return this;
  }

  public QueryBuilder indexEntries() {
    impl.indexEntries();
    return this;
  }

  public QueryBuilder indexFull() {
    impl.indexFull();
    return this;
  }

  public QueryBuilder on(String keyspace, String table) {
    impl.on(keyspace, table);
    return this;
  }

  // Overloaded on method that accepts a Table object
  public QueryBuilder on(io.stargate.db.schema.Table table) {
    impl.on(table);
    return this;
  }

  public QueryBuilder custom(String className) {
    impl.custom(className);
    return this;
  }

  public QueryBuilder options(java.util.Map<String, String> customIndexOptions) {
    impl.options(customIndexOptions);
    return this;
  }

  // TRUNCATE operations
  public QueryBuilder truncate() {
    impl.truncate();
    return this;
  }

  // Additional methods for conditions
  public QueryBuilder ifs(java.util.List<BuiltCondition> conditions) {
    impl.ifs(conditions);
    return this;
  }

  // Overloaded ifs method that accepts a single BuiltCondition
  public QueryBuilder ifs(BuiltCondition condition) {
    impl.ifs(condition);
    return this;
  }

  // TTL without parameter (for prepared statements)
  public QueryBuilder ttl() {
    impl.ttl();
    return this;
  }

  // Timestamp method without parameter (for prepared statements)
  public QueryBuilder timestamp() {
    impl.timestamp();
    return this;
  }

  // Timestamp method with value
  public QueryBuilder timestamp(Long timestamp) {
    impl.timestamp(timestamp);
    return this;
  }

  // Overloaded value method that accepts a Collection
  public QueryBuilder value(java.util.Collection<ValueModifier> modifiers) {
    impl.value(modifiers);
    return this;
  }

  // Indexing type method for collection indexes
  public QueryBuilder indexingType(io.stargate.db.schema.CollectionIndexingType indexingType) {
    impl.indexingType(indexingType);
    return this;
  }

  // Allow filtering
  public QueryBuilder allowFiltering() {
    impl.allowFiltering();
    return this;
  }

  // Overloaded allowFiltering method that accepts a boolean parameter
  public QueryBuilder allowFiltering(boolean allowFiltering) {
    impl.allowFiltering(allowFiltering);
    return this;
  }

  // Index method with no parameters
  public QueryBuilder index() {
    impl.index();
    return this;
  }

  // Rename column method that accepts List<Pair<String,String>>
  public QueryBuilder renameColumn(
      java.util.List<org.javatuples.Pair<String, String>> columnRenames) {
    impl.renameColumn(columnRenames);
    return this;
  }

  // Custom method that accepts String and Map<String,String> parameters
  public QueryBuilder custom(
      String customIndexClass, java.util.Map<String, String> customIndexOptions) {
    impl.custom(customIndexClass, customIndexOptions);
    return this;
  }
}
