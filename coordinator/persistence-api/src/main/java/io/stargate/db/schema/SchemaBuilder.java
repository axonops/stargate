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
package io.stargate.db.schema;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Simple schema builder factory.
 *
 * <p>This replaces the duzzt-generated DSL with a simple builder pattern.
 */
public class SchemaBuilder {
  private final SchemaBuilderImpl impl;

  public SchemaBuilder(Optional<Consumer<Schema>> callback) {
    this.impl = new SchemaBuilderImpl(callback);
  }

  public static SchemaBuilder create() {
    return new SchemaBuilder(Optional.empty());
  }

  public static SchemaBuilder create(Consumer<Schema> callback) {
    return new SchemaBuilder(Optional.of(callback));
  }

  // Fluent wrapper methods
  public SchemaBuilder keyspace(String name) {
    impl.keyspace(name);
    return this;
  }

  public SchemaBuilder column(String name) {
    impl.column(name);
    return this;
  }

  public SchemaBuilder table(String name) {
    impl.table(name);
    return this;
  }

  public SchemaBuilder column(String name, Column.Type type) {
    impl.column(name, type);
    return this;
  }

  public SchemaBuilder column(String name, Column.Type type, Column.Kind kind) {
    impl.column(name, type, kind);
    return this;
  }

  public SchemaBuilder column(String name, Column.Kind kind) {
    impl.column(name, kind);
    return this;
  }

  public SchemaBuilder column(String name, Column.Kind kind, Column.Order order) {
    impl.column(name, kind, order);
    return this;
  }

  public SchemaBuilder column(String name, Column.ColumnType type) {
    impl.column(name, type);
    return this;
  }

  public SchemaBuilder column(String name, Column.ColumnType type, Column.Kind kind) {
    impl.column(name, type, kind);
    return this;
  }

  public SchemaBuilder column(String name, Column.Type type, Column.Kind kind, Column.Order order) {
    impl.column(name, type, kind, order);
    return this;
  }

  public SchemaBuilder column(
      String name, Column.ColumnType type, Column.Kind kind, Column.Order order) {
    impl.column(name, type, kind, order);
    return this;
  }

  public SchemaBuilder type(String typeName) {
    impl.type(typeName);
    return this;
  }

  public SchemaBuilder materializedView(String name) {
    impl.materializedView(name);
    return this;
  }

  public SchemaBuilder withReplication(java.util.Map<String, String> replication) {
    impl.withReplication(replication);
    return this;
  }

  public SchemaBuilder secondaryIndex(String indexName) {
    impl.secondaryIndex(indexName);
    return this;
  }

  public SchemaBuilder indexClass(String indexClass) {
    impl.indexClass(indexClass);
    return this;
  }

  public SchemaBuilder indexOptions(java.util.Map<String, String> indexOptions) {
    impl.indexOptions(indexOptions);
    return this;
  }

  public SchemaBuilder andDurableWrites(boolean durableWrites) {
    impl.andDurableWrites(durableWrites);
    return this;
  }

  public Schema build() {
    return impl.build();
  }
}
