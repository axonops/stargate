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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentTableProperties;
import io.stargate.sgv2.docsapi.service.common.model.RowWrapper;
import io.stargate.sgv2.docsapi.service.query.condition.BaseCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@ExtendWith(MockitoExtension.class)
class LikeConditionTest {

  @Mock DocumentProperties documentProperties;
  @Mock DocumentTableProperties tableProperties;
  @Mock RowWrapper row;

  @BeforeEach
  void setup() {
    when(documentProperties.tableProperties()).thenReturn(tableProperties);
    when(tableProperties.stringValueColumnName()).thenReturn("text_value");
  }

  @Test
  void testValidation() {
    // Test empty pattern
    assertThatThrownBy(() -> ImmutableLikeCondition.of("", false, documentProperties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("LIKE pattern cannot be null or empty");
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
    "'exact', 'exacto', false"
  })
  void testLikePatternMatching(String pattern, String value, boolean expected) {
    // given
    LikeCondition condition = ImmutableLikeCondition.of(pattern, false, documentProperties);
    when(row.getString("text_value")).thenReturn(value);

    // when
    boolean result = condition.test(row);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void testNotLike() {
    // given
    LikeCondition condition = ImmutableLikeCondition.of("test%", true, documentProperties);

    // Test matching value (should return false for NOT LIKE)
    when(row.getString("text_value")).thenReturn("testing");
    assertThat(condition.test(row)).isFalse();

    // Test non-matching value (should return true for NOT LIKE)
    when(row.getString("text_value")).thenReturn("other");
    assertThat(condition.test(row)).isTrue();
  }

  @Test
  void testNullHandling() {
    // given
    LikeCondition likeCondition = ImmutableLikeCondition.of("test%", false, documentProperties);
    LikeCondition notLikeCondition = ImmutableLikeCondition.of("test%", true, documentProperties);
    when(row.getString("text_value")).thenReturn(null);

    // when/then
    // LIKE with null should return false
    assertThat(likeCondition.test(row)).isFalse();
    assertThat(likeCondition.isEvaluateOnMissingFields()).isFalse();

    // NOT LIKE with null should return true
    assertThat(notLikeCondition.test(row)).isTrue();
    assertThat(notLikeCondition.isEvaluateOnMissingFields()).isTrue();
  }

  @Test
  void testNegate() {
    // given
    LikeCondition condition = ImmutableLikeCondition.of("test%", false, documentProperties);

    // when
    BaseCondition negated = condition.negate();

    // then
    assertThat(negated).isInstanceOf(LikeCondition.class);
    LikeCondition negatedLike = (LikeCondition) negated;
    assertThat(negatedLike.getPattern()).isEqualTo("test%");
    assertThat(negatedLike.isNegated()).isTrue();

    // Test double negation
    BaseCondition doubleNegated = negated.negate();
    assertThat(doubleNegated).isInstanceOf(LikeCondition.class);
    LikeCondition doubleNegatedLike = (LikeCondition) doubleNegated;
    assertThat(doubleNegatedLike.getPattern()).isEqualTo("test%");
    assertThat(doubleNegatedLike.isNegated()).isFalse();
  }

  @Test
  void testGetBuiltCondition() {
    // given
    LikeCondition condition = ImmutableLikeCondition.of("test%", false, documentProperties);

    // when
    var builtCondition = condition.getBuiltCondition();

    // then
    // Currently returns empty until LIKE is added to Predicate enum
    assertThat(builtCondition).isEmpty();
  }

  @Test
  void testSpecialCharactersInPattern() {
    // Test patterns with regex special characters
    LikeCondition condition = ImmutableLikeCondition.of("test.file[1]", false, documentProperties);

    // Should match exact string (special chars are escaped)
    when(row.getString("text_value")).thenReturn("test.file[1]");
    assertThat(condition.test(row)).isTrue();

    // Should not match regex interpretation
    when(row.getString("text_value")).thenReturn("testXfile1");
    assertThat(condition.test(row)).isFalse();
  }

  @Test
  void testComplexPatterns() {
    // Test complex pattern with multiple wildcards
    LikeCondition condition = ImmutableLikeCondition.of("a%b_c%d", false, documentProperties);

    // Should match
    when(row.getString("text_value")).thenReturn("aXXbYcZZd");
    assertThat(condition.test(row)).isTrue();

    when(row.getString("text_value")).thenReturn("abXcd");
    assertThat(condition.test(row)).isTrue();

    // Should not match
    when(row.getString("text_value")).thenReturn("aXXbYYcZZd"); // Two chars between b and c
    assertThat(condition.test(row)).isFalse();

    when(row.getString("text_value")).thenReturn("aXXbc"); // Missing 'd' at end
    assertThat(condition.test(row)).isFalse();
  }

  @Test
  void testQueryValueType() {
    // given
    LikeCondition condition = ImmutableLikeCondition.of("test%", false, documentProperties);

    // when/then
    assertThat(condition.getQueryValueType()).isEqualTo(String.class);
    assertThat(condition.getQueryValue()).isEqualTo("test%");
  }
}
