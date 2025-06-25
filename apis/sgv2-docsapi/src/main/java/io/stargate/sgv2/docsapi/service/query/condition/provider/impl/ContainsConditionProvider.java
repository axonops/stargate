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

import com.fasterxml.jackson.databind.JsonNode;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.service.query.condition.BaseCondition;
import io.stargate.sgv2.docsapi.service.query.condition.impl.ContainsCondition;
import io.stargate.sgv2.docsapi.service.query.condition.impl.ImmutableContainsCondition;
import io.stargate.sgv2.docsapi.service.query.condition.provider.ConditionProvider;
import io.stargate.sgv2.docsapi.service.query.filter.operation.ValueFilterOperation;
import io.stargate.sgv2.docsapi.service.query.filter.operation.impl.ContainsFilterOperation;
import java.util.Optional;

/**
 * ConditionProvider for CONTAINS operations. Handles array/collection containment queries. Note:
 * NOT CONTAINS is not supported in Cassandra 5.0.
 */
public class ContainsConditionProvider implements ConditionProvider {

  public static ContainsConditionProvider of(boolean negated) {
    if (negated) {
      throw new UnsupportedOperationException("NOT CONTAINS is not supported in Cassandra 5.0");
    }
    return new ContainsConditionProvider();
  }

  private ContainsConditionProvider() {
    // No parameters needed
  }

  /** {@inheritDoc} */
  @Override
  public Optional<? extends BaseCondition> createCondition(
      JsonNode node, DocumentProperties documentProperties, boolean numericBooleans) {

    // CONTAINS works with any value type
    Object queryValue;
    Class<?> valueType;

    if (node.isTextual()) {
      queryValue = node.asText();
      valueType = String.class;
    } else if (node.isBoolean()) {
      queryValue = node.asBoolean();
      valueType = Boolean.class;
    } else if (node.isNumber()) {
      queryValue = node.numberValue();
      valueType = Number.class;
    } else if (node.isNull()) {
      // CONTAINS with null is not supported
      return Optional.empty();
    } else {
      // Complex types (objects, arrays) not supported for CONTAINS
      return Optional.empty();
    }

    // Get the filter operation (only CONTAINS is supported in Cassandra 5.0)
    ValueFilterOperation operation = ContainsFilterOperation.of();

    ContainsCondition condition =
        ImmutableContainsCondition.of(operation, queryValue, documentProperties, valueType);

    return Optional.of(condition);
  }
}
