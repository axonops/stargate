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
import io.stargate.sgv2.docsapi.api.exception.ErrorCode;
import io.stargate.sgv2.docsapi.api.exception.ErrorCodeRuntimeException;
import io.stargate.sgv2.docsapi.service.query.filter.operation.FilterOperationCode;
import io.stargate.sgv2.docsapi.service.query.filter.operation.ValueFilterOperation;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.immutables.value.Value;

/**
 * Filter operation for LIKE pattern matching. This operation only supports string values and uses
 * SQL LIKE syntax with % and _ wildcards.
 */
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
@Value.Immutable(singleton = true)
public abstract class LikeFilterOperation implements ValueFilterOperation {

  /**
   * @return Singleton instance
   */
  public static LikeFilterOperation of() {
    return ImmutableLikeFilterOperation.of();
  }

  /** {@inheritDoc} */
  @Override
  public FilterOperationCode getOpCode() {
    return FilterOperationCode.LIKE;
  }

  /** {@inheritDoc} */
  @Override
  public Optional<Predicate> getQueryPredicate() {
    return Optional.of(Predicate.LIKE);
  }

  /** {@inheritDoc} */
  @Override
  public boolean test(String dbValue, String filterValue) {
    if (dbValue == null || filterValue == null) {
      return false;
    }

    // Convert LIKE pattern to regex and test
    Pattern pattern = convertLikeToRegexPattern(filterValue);
    return pattern.matcher(dbValue).matches();
  }

  /** {@inheritDoc} */
  @Override
  public void validateStringFilterInput(String filterValue) {
    if (filterValue == null || filterValue.isEmpty()) {
      throw new ErrorCodeRuntimeException(
          ErrorCode.DOCS_API_SEARCH_FILTER_INVALID, "LIKE pattern cannot be null or empty");
    }

    // Validate the pattern can be converted to regex
    try {
      convertLikeToRegexPattern(filterValue);
    } catch (PatternSyntaxException e) {
      throw new ErrorCodeRuntimeException(
          ErrorCode.DOCS_API_SEARCH_FILTER_INVALID, "Invalid LIKE pattern: " + filterValue);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean test(Double dbValue, Number filterValue) {
    // LIKE only works with strings
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void validateNumberFilterInput(Number filterValue) {
    throw new ErrorCodeRuntimeException(
        ErrorCode.DOCS_API_SEARCH_FILTER_INVALID, "LIKE operation does not support numeric values");
  }

  /** {@inheritDoc} */
  @Override
  public boolean test(Boolean dbValue, Boolean filterValue) {
    // LIKE only works with strings
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void validateBooleanFilterInput(Boolean filterValue) {
    throw new ErrorCodeRuntimeException(
        ErrorCode.DOCS_API_SEARCH_FILTER_INVALID, "LIKE operation does not support boolean values");
  }

  /** {@inheritDoc} */
  @Override
  public boolean isEvaluateOnMissingFields() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public ValueFilterOperation negate() {
    // NOT LIKE is not supported in Cassandra 5.0
    throw new UnsupportedOperationException("NOT LIKE is not supported in Cassandra 5.0");
  }

  /**
   * Converts SQL LIKE pattern to Java regex pattern. - % becomes .* - _ becomes . - Other special
   * regex characters are escaped
   */
  private Pattern convertLikeToRegexPattern(String likePattern) {
    // Escape special regex characters except % and _
    String escaped = likePattern.replaceAll("([\\[\\]{}()\\^$\\+\\*\\?\\\\\\|\\.])", "\\\\$1");

    // Convert LIKE wildcards to regex
    String regex = "^" + escaped.replace("%", ".*").replace("_", ".") + "$";

    return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
  }
}
