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
package io.stargate.sgv2.docsapi.service.query.condition.impl;

import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.sgv2.api.common.cql.builder.BuiltCondition;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.service.common.model.RowWrapper;
import io.stargate.sgv2.docsapi.service.query.condition.BaseCondition;
import io.stargate.sgv2.docsapi.service.query.filter.operation.FilterOperationCode;
import io.stargate.sgv2.docsapi.service.query.filter.operation.ValueFilterOperation;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.immutables.value.Value;

/**
 * Condition that implements CONTAINS KEY operation for object/map fields. In the Document API, this
 * checks if a specific key exists within an object by looking for rows with the appropriate path.
 */
@Value.Immutable
public abstract class ContainsKeyCondition implements BaseCondition {

  /**
   * @return The filter operation (CONTAINS KEY)
   */
  @Value.Parameter
  public abstract ValueFilterOperation getFilterOperation();

  /**
   * @return The key to search for in the object
   */
  @Value.Parameter
  public abstract String getKeyToFind();

  /**
   * @return The reference to DocumentProperties
   */
  @Value.Parameter
  public abstract DocumentProperties documentProperties();

  /** Validates the condition parameters. */
  @Value.Check
  protected void validate() {
    if (getKeyToFind() == null || getKeyToFind().isEmpty()) {
      throw new IllegalArgumentException("CONTAINS KEY query value cannot be null or empty");
    }
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Pair<BuiltCondition, QueryOuterClass.Value>> getBuiltCondition() {
    // CONTAINS KEY doesn't translate directly to a CQL condition
    // It's implemented by checking for the existence of a specific path
    // The actual query building is handled by a specialized query builder
    return Optional.empty();
  }

  /** {@inheritDoc} */
  @Override
  public FilterOperationCode getFilterOperationCode() {
    return getFilterOperation().getOpCode();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEvaluateOnMissingFields() {
    return getFilterOperation().isEvaluateOnMissingFields();
  }

  /** {@inheritDoc} */
  @Override
  public boolean test(RowWrapper row) {
    // For in-memory filtering, we need to check if the row's path
    // ends with the key we're looking for

    // This is handled differently in the query builder for persistence queries
    // For in-memory, we would need the FilterPath context to properly evaluate
    // For now, return false as this should primarily be a persistence condition
    return false;
  }

  @Override
  public BaseCondition negate() {
    // NOT CONTAINS KEY is not supported in Cassandra 5.0
    throw new UnsupportedOperationException("NOT CONTAINS KEY is not supported in Cassandra 5.0");
  }

  /** {@inheritDoc} */
  @Override
  public Object getQueryValue() {
    return getKeyToFind();
  }
}
