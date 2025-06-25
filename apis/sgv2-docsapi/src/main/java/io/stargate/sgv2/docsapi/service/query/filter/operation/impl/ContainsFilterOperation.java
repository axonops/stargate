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
package io.stargate.sgv2.docsapi.service.query.filter.operation.impl;

import io.stargate.sgv2.api.common.cql.builder.Predicate;
import io.stargate.sgv2.docsapi.service.query.filter.operation.FilterOperationCode;
import io.stargate.sgv2.docsapi.service.query.filter.operation.ValueFilterOperation;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Filter operation for CONTAINS matching. This operation checks if a collection (array) contains a
 * specific value. In the Document API, arrays are stored as individual rows with array indices in
 * the path.
 */
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
@Value.Immutable(singleton = true)
public abstract class ContainsFilterOperation implements ValueFilterOperation {

  /**
   * @return Singleton instance
   */
  public static ContainsFilterOperation of() {
    return ImmutableContainsFilterOperation.of();
  }

  /** {@inheritDoc} */
  @Override
  public FilterOperationCode getOpCode() {
    return FilterOperationCode.CONTAINS;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Predicate> getQueryPredicate() {
    // For collections stored as individual rows, we use equality on the value columns
    // The array filtering logic is handled at a higher level by adding array path patterns
    return Optional.of(Predicate.EQ);
  }

  /** {@inheritDoc} */
  @Override
  public boolean test(String dbValue, String filterValue) {
    // In the context of Document API, this would test if a specific array element equals the value
    // The actual CONTAINS logic is implemented at the condition level
    if (dbValue == null || filterValue == null) {
      return false;
    }
    return dbValue.equals(filterValue);
  }

  /** {@inheritDoc} */
  @Override
  public boolean test(Double dbValue, Number filterValue) {
    if (dbValue == null || filterValue == null) {
      return false;
    }
    return dbValue.equals(filterValue.doubleValue());
  }

  /** {@inheritDoc} */
  @Override
  public boolean test(Boolean dbValue, Boolean filterValue) {
    if (dbValue == null || filterValue == null) {
      return false;
    }
    return dbValue.equals(filterValue);
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEvaluateOnMissingFields() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public ValueFilterOperation negate() {
    // NOT CONTAINS is not supported in Cassandra 5.0
    throw new UnsupportedOperationException("NOT CONTAINS is not supported in Cassandra 5.0");
  }
}
