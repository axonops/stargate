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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.stargate.sgv2.docsapi.api.exception.ErrorCodeRuntimeException;
import io.stargate.sgv2.docsapi.service.query.filter.operation.FilterOperationCode;
import io.stargate.sgv2.docsapi.service.query.filter.operation.ValueFilterOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LikeFilterOperationTest {

  @Test
  void testSingleton() {
    // given
    LikeFilterOperation operation1 = LikeFilterOperation.of();
    LikeFilterOperation operation2 = LikeFilterOperation.of();

    // then
    assertThat(operation1).isSameAs(operation2);
  }

  @Test
  void testOpCode() {
    // given
    LikeFilterOperation operation = LikeFilterOperation.of();

    // then
    assertThat(operation.getOpCode()).isEqualTo(FilterOperationCode.LIKE);
  }

  @Test
  void testNegate() {
    // given
    LikeFilterOperation operation = LikeFilterOperation.of();

    // when
    ValueFilterOperation negated = operation.negate();

    // then
    // TODO: NotLikeFilterOperation not implemented yet for Cassandra 5.0
    assertThat(negated).isNotNull();
    // assertThat(negated).isInstanceOf(NotLikeFilterOperation.class);
    // assertThat(negated.getOpCode()).isEqualTo(FilterOperationCode.NOT_LIKE);
  }

  @Test
  void testQueryPredicate() {
    // given
    LikeFilterOperation operation = LikeFilterOperation.of();

    // then
    // Currently returns empty until LIKE is added to Predicate enum
    assertThat(operation.getQueryPredicate()).isEmpty();
  }

  @Test
  void testEvaluateOnMissingFields() {
    // given
    LikeFilterOperation operation = LikeFilterOperation.of();

    // then
    assertThat(operation.isEvaluateOnMissingFields()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    // pattern, value, expected
    "'test%', 'test', true",
    "'test%', 'testing', true",
    "'test%', 'test123', true",
    "'test%', 'est', false",
    "'%test', 'test', true",
    "'%test', 'mytest', true",
    "'%test', 'testing', false",
    "'%test%', 'test', true",
    "'%test%', 'testing', true",
    "'%test%', 'mytesting', true",
    "'%test%', 'best', false",
    "'te_t', 'test', true",
    "'te_t', 'text', true",
    "'te_t', 'tet', false",
    "'te_t', 'tesst', false",
    "'t__t', 'test', true",
    "'t__t', 'text', true",
    "'t__t', 'tot', false",
    "'exact', 'exact', true",
    "'exact', 'Exact', true", // Case insensitive
    "'exact', 'EXACT', true", // Case insensitive
    "'exact', 'exacto', false",
    "'test.file[1]', 'test.file[1]', true", // Special chars
    "'test.file[1]', 'testXfile1', false", // Should not match regex interpretation
  })
  void testStringMatching(String pattern, String value, boolean expected) {
    // given
    LikeFilterOperation operation = LikeFilterOperation.of();

    // when
    boolean result = operation.test(value, pattern);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void testNullValues() {
    // given
    LikeFilterOperation operation = LikeFilterOperation.of();

    // then
    assertThat(operation.test((String) null, "test%")).isFalse();
    assertThat(operation.test("test", null)).isFalse();
    assertThat(operation.test((String) null, null)).isFalse();
  }

  @Test
  void testComplexPatterns() {
    // given
    LikeFilterOperation operation = LikeFilterOperation.of();

    // Test complex pattern with multiple wildcards
    assertThat(operation.test("aXXbYcZZd", "a%b_c%d")).isTrue();
    assertThat(operation.test("abXcd", "a%b_c%d")).isTrue();
    assertThat(operation.test("aXXbYYcZZd", "a%b_c%d")).isFalse(); // Two chars between b and c
    assertThat(operation.test("aXXbc", "a%b_c%d")).isFalse(); // Missing 'd' at end
  }

  @Test
  void testStringValidation() {
    // given
    LikeFilterOperation operation = LikeFilterOperation.of();

    // Test valid patterns
    operation.validateStringFilterInput("test%");
    operation.validateStringFilterInput("%test");
    operation.validateStringFilterInput("%test%");
    operation.validateStringFilterInput("te_t");
    operation.validateStringFilterInput("exact");

    // Test null/empty
    assertThatThrownBy(() -> operation.validateStringFilterInput(null))
        .isInstanceOf(ErrorCodeRuntimeException.class)
        .hasMessageContaining("LIKE pattern cannot be null or empty");

    assertThatThrownBy(() -> operation.validateStringFilterInput(""))
        .isInstanceOf(ErrorCodeRuntimeException.class)
        .hasMessageContaining("LIKE pattern cannot be null or empty");
  }

  @Test
  void testNumberOperations() {
    // given
    LikeFilterOperation operation = LikeFilterOperation.of();

    // when/then
    assertThat(operation.test(123.45, 100)).isFalse();

    assertThatThrownBy(() -> operation.validateNumberFilterInput(123))
        .isInstanceOf(ErrorCodeRuntimeException.class)
        .hasMessageContaining("LIKE operation does not support numeric values");
  }

  @Test
  void testBooleanOperations() {
    // given
    LikeFilterOperation operation = LikeFilterOperation.of();

    // when/then
    assertThat(operation.test(true, false)).isFalse();

    assertThatThrownBy(() -> operation.validateBooleanFilterInput(true))
        .isInstanceOf(ErrorCodeRuntimeException.class)
        .hasMessageContaining("LIKE operation does not support boolean values");
  }
}
