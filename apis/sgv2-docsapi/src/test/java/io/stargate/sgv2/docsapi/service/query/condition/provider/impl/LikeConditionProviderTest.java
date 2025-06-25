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
package io.stargate.sgv2.docsapi.service.query.condition.provider.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentTableProperties;
import io.stargate.sgv2.docsapi.service.query.condition.BaseCondition;
import io.stargate.sgv2.docsapi.service.query.condition.impl.LikeCondition;
import io.stargate.sgv2.docsapi.service.query.filter.operation.FilterOperationCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@QuarkusTest
@ExtendWith(MockitoExtension.class)
class LikeConditionProviderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock DocumentProperties documentProperties;
  @Mock DocumentTableProperties tableProperties;

  @BeforeEach
  void setup() {
    when(documentProperties.tableProperties()).thenReturn(tableProperties);
    when(tableProperties.stringValueColumnName()).thenReturn("text_value");
  }

  @Test
  void testCreateConditionWithTextNode() throws Exception {
    // given
    LikeConditionProvider provider = LikeConditionProvider.of(false);
    JsonNode node = MAPPER.readTree("\"test%\"");

    // when
    Optional<? extends BaseCondition> result =
        provider.createCondition(node, documentProperties, false);

    // then
    assertThat(result).isPresent();
    assertThat(result.get()).isInstanceOf(LikeCondition.class);

    LikeCondition condition = (LikeCondition) result.get();
    assertThat(condition.getPattern()).isEqualTo("test%");
    assertThat(condition.isNegated()).isFalse();
    assertThat(condition.getFilterOperationCode()).isEqualTo(FilterOperationCode.LIKE);
  }

  @Test
  void testCreateConditionWithNegated() throws Exception {
    // given
    LikeConditionProvider provider = LikeConditionProvider.of(true);
    JsonNode node = MAPPER.readTree("\"test%\"");

    // when
    Optional<? extends BaseCondition> result =
        provider.createCondition(node, documentProperties, false);

    // then
    assertThat(result).isPresent();
    assertThat(result.get()).isInstanceOf(LikeCondition.class);

    LikeCondition condition = (LikeCondition) result.get();
    assertThat(condition.getPattern()).isEqualTo("test%");
    assertThat(condition.isNegated()).isTrue();
    assertThat(condition.getFilterOperationCode()).isEqualTo(FilterOperationCode.LIKE);
  }

  @Test
  void testCreateConditionWithNumberNode() throws Exception {
    // given
    LikeConditionProvider provider = LikeConditionProvider.of(false);
    JsonNode node = MAPPER.readTree("123");

    // when
    Optional<? extends BaseCondition> result =
        provider.createCondition(node, documentProperties, false);

    // then
    // Should return empty for non-string values
    assertThat(result).isEmpty();
  }

  @Test
  void testCreateConditionWithBooleanNode() throws Exception {
    // given
    LikeConditionProvider provider = LikeConditionProvider.of(false);
    JsonNode node = MAPPER.readTree("true");

    // when
    Optional<? extends BaseCondition> result =
        provider.createCondition(node, documentProperties, false);

    // then
    // Should return empty for non-string values
    assertThat(result).isEmpty();
  }

  @Test
  void testCreateConditionWithNullNode() throws Exception {
    // given
    LikeConditionProvider provider = LikeConditionProvider.of(false);
    JsonNode node = MAPPER.readTree("null");

    // when
    Optional<? extends BaseCondition> result =
        provider.createCondition(node, documentProperties, false);

    // then
    // Should return empty for null values
    assertThat(result).isEmpty();
  }

  @Test
  void testCreateConditionWithObjectNode() throws Exception {
    // given
    LikeConditionProvider provider = LikeConditionProvider.of(false);
    JsonNode node = MAPPER.readTree("{\"test\": \"value\"}");

    // when
    Optional<? extends BaseCondition> result =
        provider.createCondition(node, documentProperties, false);

    // then
    // Should return empty for object values
    assertThat(result).isEmpty();
  }

  @Test
  void testCreateConditionWithArrayNode() throws Exception {
    // given
    LikeConditionProvider provider = LikeConditionProvider.of(false);
    JsonNode node = MAPPER.readTree("[\"test\", \"value\"]");

    // when
    Optional<? extends BaseCondition> result =
        provider.createCondition(node, documentProperties, false);

    // then
    // Should return empty for array values
    assertThat(result).isEmpty();
  }

  @Test
  void testCreateConditionWithComplexPattern() throws Exception {
    // given
    LikeConditionProvider provider = LikeConditionProvider.of(false);
    JsonNode node = MAPPER.readTree("\"a%b_c%d\"");

    // when
    Optional<? extends BaseCondition> result =
        provider.createCondition(node, documentProperties, false);

    // then
    assertThat(result).isPresent();
    assertThat(result.get()).isInstanceOf(LikeCondition.class);

    LikeCondition condition = (LikeCondition) result.get();
    assertThat(condition.getPattern()).isEqualTo("a%b_c%d");
  }
}
