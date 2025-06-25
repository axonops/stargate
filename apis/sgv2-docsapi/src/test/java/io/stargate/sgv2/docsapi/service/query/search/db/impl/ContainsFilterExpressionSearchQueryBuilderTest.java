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
package io.stargate.sgv2.docsapi.service.query.search.db.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.quarkus.test.junit.QuarkusTest;
import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentTableProperties;
import io.stargate.sgv2.docsapi.service.query.FilterExpression;
import io.stargate.sgv2.docsapi.service.query.FilterPath;
import io.stargate.sgv2.docsapi.service.query.ImmutableFilterExpression;
import io.stargate.sgv2.docsapi.service.query.ImmutableFilterPath;
import io.stargate.sgv2.docsapi.service.query.condition.impl.ContainsCondition;
import io.stargate.sgv2.docsapi.service.query.condition.impl.ImmutableContainsCondition;
import io.stargate.sgv2.docsapi.service.query.condition.impl.ImmutableStringCondition;
import io.stargate.sgv2.docsapi.service.query.condition.impl.StringCondition;
import io.stargate.sgv2.docsapi.service.query.filter.operation.impl.ContainsFilterOperation;
import io.stargate.sgv2.docsapi.service.query.filter.operation.impl.EqFilterOperation;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@ExtendWith(MockitoExtension.class)
class ContainsFilterExpressionSearchQueryBuilderTest {

  @Mock DocumentProperties documentProperties;
  @Mock DocumentTableProperties tableProperties;

  @BeforeEach
  void setup() {
    when(documentProperties.tableProperties()).thenReturn(tableProperties);
    when(documentProperties.maxDepth()).thenReturn(10);
    when(tableProperties.keyColumnName()).thenReturn("key");
    when(tableProperties.leafColumnName()).thenReturn("leaf");
    when(tableProperties.stringValueColumnName()).thenReturn("text_value");
    when(tableProperties.pathColumnName(0)).thenReturn("p0");
    when(tableProperties.pathColumnName(1)).thenReturn("p1");
    when(tableProperties.pathColumnName(2)).thenReturn("p2");
    when(tableProperties.pathColumnName(3)).thenReturn("p3");
  }

  @Test
  void testSimpleArrayContains() {
    // given
    FilterPath path = ImmutableFilterPath.of(Arrays.asList("tags"));
    ContainsCondition condition =
        ImmutableContainsCondition.of(
            ContainsFilterOperation.of(), "javascript", documentProperties, String.class);
    FilterExpression expression =
        ImmutableFilterExpression.builder()
            .filterPath(path)
            .condition(condition)
            .orderIndex(0)
            .build();

    ContainsFilterExpressionSearchQueryBuilder builder =
        new ContainsFilterExpressionSearchQueryBuilder(documentProperties, expression);

    // when
    QueryOuterClass.Query query = builder.buildQuery("test", "collection", "key", "leaf");

    // then
    String cql = query.getCql();
    assertThat(cql).contains("WHERE");
    assertThat(cql).contains("p0 = ?"); // array field name
    assertThat(cql).contains("p1 LIKE ?"); // array index pattern
    assertThat(cql).contains("p2 = ?"); // no further nesting
    assertThat(cql).contains("text_value = ?"); // value match

    // Check the bind values
    QueryOuterClass.Query boundQuery = builder.bind(query);
    assertThat(boundQuery.getValues().getValuesList()).hasSize(4);
  }

  @Test
  void testNestedArrayContains() {
    // given - array at nested path like user.favorites
    FilterPath path = ImmutableFilterPath.of(Arrays.asList("user", "favorites"));
    ContainsCondition condition =
        ImmutableContainsCondition.of(
            ContainsFilterOperation.of(), "coffee", documentProperties, String.class);
    FilterExpression expression =
        ImmutableFilterExpression.builder()
            .filterPath(path)
            .condition(condition)
            .orderIndex(0)
            .build();

    ContainsFilterExpressionSearchQueryBuilder builder =
        new ContainsFilterExpressionSearchQueryBuilder(documentProperties, expression);

    // when
    QueryOuterClass.Query query = builder.buildQuery("test", "collection", "key", "leaf");

    // then
    String cql = query.getCql();
    assertThat(cql).contains("p0 = ?"); // user
    assertThat(cql).contains("p1 = ?"); // favorites
    assertThat(cql).contains("p2 LIKE ?"); // array index pattern
    assertThat(cql).contains("p3 = ?"); // no further nesting
    assertThat(cql).contains("text_value = ?"); // value match
  }

  @Test
  void testRejectsNonContainsCondition() {
    // given - a non-CONTAINS condition
    FilterPath path = ImmutableFilterPath.of(Arrays.asList("name"));
    StringCondition condition =
        ImmutableStringCondition.of(EqFilterOperation.of(), "John", documentProperties);
    FilterExpression expression =
        ImmutableFilterExpression.builder()
            .filterPath(path)
            .condition(condition)
            .orderIndex(0)
            .build();

    // when/then
    assertThatThrownBy(
            () -> new ContainsFilterExpressionSearchQueryBuilder(documentProperties, expression))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only accepts CONTAINS conditions");
  }

  @Test
  void testNumericContains() {
    // given
    FilterPath path = ImmutableFilterPath.of(Arrays.asList("scores"));
    ContainsCondition condition =
        ImmutableContainsCondition.of(
            ContainsFilterOperation.of(), 95, documentProperties, Number.class);
    FilterExpression expression =
        ImmutableFilterExpression.builder()
            .filterPath(path)
            .condition(condition)
            .orderIndex(0)
            .build();

    ContainsFilterExpressionSearchQueryBuilder builder =
        new ContainsFilterExpressionSearchQueryBuilder(documentProperties, expression);

    // when
    QueryOuterClass.Query query = builder.buildQuery("test", "collection");

    // then
    String cql = query.getCql();
    assertThat(cql).contains("double_value = ?"); // numeric value column
    assertThat(cql).contains("p1 LIKE ?"); // array index pattern
  }
}
