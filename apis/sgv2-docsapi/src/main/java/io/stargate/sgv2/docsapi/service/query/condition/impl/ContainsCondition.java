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

import io.stargate.bridge.grpc.Values;
import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.sgv2.api.common.cql.builder.BuiltCondition;
import io.stargate.sgv2.api.common.cql.builder.Predicate;
import io.stargate.sgv2.api.common.cql.builder.Term;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.service.common.model.RowWrapper;
import io.stargate.sgv2.docsapi.service.query.condition.BaseCondition;
import io.stargate.sgv2.docsapi.service.query.filter.operation.FilterOperationCode;
import io.stargate.sgv2.docsapi.service.query.filter.operation.ValueFilterOperation;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.immutables.value.Value;

/**
 * Condition that implements CONTAINS operation for array/collection fields. In the Document API,
 * arrays are stored as individual rows with array indices in the path. This condition handles the
 * logic of matching values within arrays.
 *
 * <p>Note: This condition works differently from other conditions because it needs to match rows
 * where the path includes an array index pattern AND the value matches.
 */
@Value.Immutable
public abstract class ContainsCondition implements BaseCondition {

  /**
   * @return The filter operation (CONTAINS or NOT_CONTAINS)
   */
  @Value.Parameter
  public abstract ValueFilterOperation getFilterOperation();

  /**
   * @return The value to search for in the collection
   */
  @Value.Parameter
  public abstract Object getQueryValue();

  /**
   * @return The reference to DocumentProperties
   */
  @Value.Parameter
  public abstract DocumentProperties documentProperties();

  /**
   * @return The type of the query value
   */
  @Value.Parameter
  public abstract Class<?> getQueryValueType();

  /** Validates the condition parameters. */
  @Value.Check
  protected void validate() {
    if (getQueryValue() == null) {
      throw new IllegalArgumentException("CONTAINS query value cannot be null");
    }
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Pair<BuiltCondition, QueryOuterClass.Value>> getBuiltCondition() {
    // For CONTAINS operations on arrays, we return only the value constraint.
    // The path constraints are handled by ContainsFilterExpressionSearchQueryBuilder
    // to ensure we only match values within the specified array field.

    // Determine the appropriate column based on value type
    String column;
    QueryOuterClass.Value value;

    if (getQueryValueType() == String.class) {
      column = documentProperties().tableProperties().stringValueColumnName();
      value = Values.of((String) getQueryValue());
    } else if (getQueryValueType() == Boolean.class) {
      column = documentProperties().tableProperties().booleanValueColumnName();
      value = Values.of((Boolean) getQueryValue());
    } else if (Number.class.isAssignableFrom(getQueryValueType())) {
      column = documentProperties().tableProperties().doubleValueColumnName();
      value = Values.of(((Number) getQueryValue()).doubleValue());
    } else {
      // Unsupported type, fall back to in-memory filtering
      return Optional.empty();
    }

    // Use equality predicate since we're matching exact values in array elements
    BuiltCondition condition = BuiltCondition.of(column, Predicate.EQ, Term.marker());

    return Optional.of(Pair.of(condition, value));
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
    // For in-memory filtering, we need to check if the row represents an array element
    // and if its value matches what we're looking for

    // Get the actual value from the row
    Object dbValue = null;
    if (getQueryValueType() == String.class) {
      dbValue = getString(row);
    } else if (getQueryValueType() == Boolean.class) {
      dbValue = getBoolean(row, false);
    } else if (Number.class.isAssignableFrom(getQueryValueType())) {
      dbValue = getDouble(row);
    }

    // Test using the filter operation
    if (getQueryValueType() == String.class) {
      return getFilterOperation().test((String) dbValue, (String) getQueryValue());
    } else if (getQueryValueType() == Boolean.class) {
      return getFilterOperation().test((Boolean) dbValue, (Boolean) getQueryValue());
    } else if (Number.class.isAssignableFrom(getQueryValueType())) {
      return getFilterOperation().test((Double) dbValue, (Number) getQueryValue());
    }

    return false;
  }

  @Override
  public BaseCondition negate() {
    // NOT CONTAINS is not supported in Cassandra 5.0
    throw new UnsupportedOperationException("NOT CONTAINS is not supported in Cassandra 5.0");
  }
}
