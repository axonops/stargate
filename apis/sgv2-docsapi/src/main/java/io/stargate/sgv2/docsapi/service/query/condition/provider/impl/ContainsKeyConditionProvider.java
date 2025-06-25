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
import io.stargate.sgv2.docsapi.service.query.condition.impl.ContainsKeyCondition;
import io.stargate.sgv2.docsapi.service.query.condition.impl.ImmutableContainsKeyCondition;
import io.stargate.sgv2.docsapi.service.query.condition.provider.ConditionProvider;
import io.stargate.sgv2.docsapi.service.query.filter.operation.impl.ContainsKeyFilterOperation;
import java.util.Optional;

/**
 * Condition provider for CONTAINS KEY operation. This checks if a specific key exists within an
 * object/map field.
 */
public class ContainsKeyConditionProvider implements ConditionProvider {

  /** {@inheritDoc} */
  @Override
  public Optional<? extends BaseCondition> createCondition(
      JsonNode node, DocumentProperties documentProperties, boolean numericBooleans) {
    // CONTAINS KEY expects a string value representing the key to search for
    if (node.isTextual()) {
      String keyToFind = node.asText();
      ContainsKeyCondition condition =
          ImmutableContainsKeyCondition.of(
              ContainsKeyFilterOperation.of(), keyToFind, documentProperties);
      return Optional.of(condition);
    } else {
      return Optional.empty();
    }
  }
}
