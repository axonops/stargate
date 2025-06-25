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
 * Filter operation for CONTAINS KEY. In the Document API context, this checks if a specific key
 * exists within an object field. Since objects are stored as individual rows with paths, this
 * translates to checking if a row exists with the specified path component.
 */
@Value.Immutable
public abstract class ContainsKeyFilterOperation implements ValueFilterOperation {

  /**
   * @return Singleton instance
   */
  public static ContainsKeyFilterOperation of() {
    return ImmutableContainsKeyFilterOperation.of();
  }

  /** {@inheritDoc} */
  @Override
  public FilterOperationCode getOpCode() {
    return FilterOperationCode.CONTAINS_KEY;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Predicate> getQueryPredicate() {
    // CONTAINS KEY doesn't have a direct database predicate
    // It's implemented as an EXISTS check on a specific path
    return Optional.empty();
  }

  @Override
  public boolean isEvaluateOnMissingFields() {
    return false;
  }

  /**
   * Test method for CONTAINS KEY operation. This checks if the key exists (non-null value indicates
   * existence).
   */
  @Override
  public boolean test(String dbValue, String filterValue) {
    // If dbValue is not null, the key exists
    return dbValue != null;
  }

  @Override
  public boolean test(Double dbValue, Number filterValue) {
    // For CONTAINS KEY, we only care about existence, not the value
    return dbValue != null;
  }

  @Override
  public boolean test(Boolean dbValue, Boolean filterValue) {
    // For CONTAINS KEY, we only care about existence, not the value
    return dbValue != null;
  }

  @Override
  public ValueFilterOperation negate() {
    // NOT CONTAINS KEY is not supported in Cassandra 5.0
    throw new UnsupportedOperationException("NOT CONTAINS KEY is not supported in Cassandra 5.0");
  }
}
