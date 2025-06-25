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
import io.stargate.sgv2.docsapi.service.query.condition.impl.ImmutableLikeCondition;
import io.stargate.sgv2.docsapi.service.query.condition.impl.LikeCondition;
import io.stargate.sgv2.docsapi.service.query.condition.provider.ConditionProvider;
import java.util.Optional;

/**
 * ConditionProvider for LIKE operations. Only accepts string patterns for SQL LIKE pattern
 * matching. Note: NOT LIKE is not supported in Cassandra 5.0.
 */
public class LikeConditionProvider implements ConditionProvider {

  public static LikeConditionProvider of(boolean negated) {
    if (negated) {
      throw new UnsupportedOperationException("NOT LIKE is not supported in Cassandra 5.0");
    }
    return new LikeConditionProvider();
  }

  private LikeConditionProvider() {
    // No parameters needed
  }

  /** {@inheritDoc} */
  @Override
  public Optional<? extends BaseCondition> createCondition(
      JsonNode node, DocumentProperties documentProperties, boolean numericBooleans) {

    // LIKE only works with string patterns
    if (node.isTextual()) {
      LikeCondition condition = ImmutableLikeCondition.of(node.asText(), false, documentProperties);
      return Optional.of(condition);
    }

    // For non-string values, return empty to trigger an error
    return Optional.empty();
  }
}
