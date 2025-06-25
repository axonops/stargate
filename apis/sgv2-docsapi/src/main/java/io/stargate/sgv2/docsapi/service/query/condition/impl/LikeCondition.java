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
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.commons.lang3.tuple.Pair;
import org.immutables.value.Value;

/**
 * Condition that implements LIKE operation for string pattern matching. Supports SQL LIKE syntax
 * with % (any characters) and _ (single character) wildcards. When SAI is enabled, this can
 * leverage the database's LIKE operator for efficient searching.
 */
@Value.Immutable
public abstract class LikeCondition implements BaseCondition {

  /**
   * @return The LIKE pattern to match against
   */
  @Value.Parameter
  public abstract String getPattern();

  /**
   * @return Whether the pattern is negated (NOT LIKE) - Note: NOT LIKE is not supported in
   *     Cassandra 5.0
   */
  @Value.Parameter
  public abstract boolean isNegated();

  /**
   * @return The reference to DocumentProperties
   */
  @Value.Parameter
  public abstract DocumentProperties documentProperties();

  /**
   * @return The compiled regex pattern for in-memory filtering
   */
  @Value.Lazy
  protected Pattern getCompiledPattern() {
    // Convert SQL LIKE pattern to regex pattern
    String regex = convertLikeToRegex(getPattern());
    return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
  }

  /**
   * Converts SQL LIKE pattern to Java regex pattern. - % becomes .* - _ becomes . - Other special
   * regex characters are escaped
   */
  private String convertLikeToRegex(String likePattern) {
    // Escape special regex characters except % and _
    String escaped = likePattern.replaceAll("([\\[\\]{}()\\^$\\+\\*\\?\\\\\\|\\.])", "\\\\$1");

    // Convert LIKE wildcards to regex
    return "^" + escaped.replace("%", ".*").replace("_", ".") + "$";
  }

  /** Validates the pattern. */
  @Value.Check
  protected void validate() {
    if (getPattern() == null || getPattern().isEmpty()) {
      throw new IllegalArgumentException("LIKE pattern cannot be null or empty");
    }

    // Try to compile the pattern to validate it
    try {
      getCompiledPattern();
    } catch (PatternSyntaxException e) {
      throw new IllegalArgumentException("Invalid LIKE pattern: " + getPattern(), e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Pair<BuiltCondition, QueryOuterClass.Value>> getBuiltCondition() {
    // LIKE queries with SAI are efficient at the database level
    // Note: This requires the table to have an SAI index on the string column
    String column = documentProperties().tableProperties().stringValueColumnName();
    QueryOuterClass.Value value = Values.of(getPattern());

    // Only positive LIKE is supported in Cassandra 5.0
    if (isNegated()) {
      // NOT LIKE would require in-memory filtering
      return Optional.empty();
    }

    // Use LIKE predicate for database-level filtering
    BuiltCondition condition = BuiltCondition.of(column, Predicate.LIKE, Term.marker());

    return Optional.of(Pair.of(condition, value));
  }

  /** {@inheritDoc} */
  @Override
  public FilterOperationCode getFilterOperationCode() {
    // Only LIKE is supported in Cassandra 5.0
    if (isNegated()) {
      throw new UnsupportedOperationException("NOT LIKE is not supported in Cassandra 5.0");
    }
    return FilterOperationCode.LIKE;
  }

  /** {@inheritDoc} */
  @Override
  public Object getQueryValue() {
    return getPattern();
  }

  @Override
  public Class<?> getQueryValueType() {
    return String.class;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEvaluateOnMissingFields() {
    // NOT LIKE should evaluate to true on missing fields
    return isNegated();
  }

  /** {@inheritDoc} */
  @Override
  public boolean test(RowWrapper row) {
    String dbValue = getString(row);

    // Handle null/missing values
    if (dbValue == null) {
      return isEvaluateOnMissingFields();
    }

    // Test against the pattern
    boolean matches = getCompiledPattern().matcher(dbValue).matches();
    return isNegated() ? !matches : matches;
  }

  @Override
  public BaseCondition negate() {
    // NOT LIKE is not supported in Cassandra 5.0
    throw new UnsupportedOperationException("NOT LIKE is not supported in Cassandra 5.0");
  }
}
