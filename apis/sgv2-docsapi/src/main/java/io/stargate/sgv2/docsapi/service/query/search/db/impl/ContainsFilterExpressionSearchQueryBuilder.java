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

import io.stargate.bridge.grpc.Values;
import io.stargate.bridge.proto.QueryOuterClass;
import io.stargate.sgv2.api.common.cql.builder.BuiltCondition;
import io.stargate.sgv2.api.common.cql.builder.Predicate;
import io.stargate.sgv2.api.common.cql.builder.Term;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentTableProperties;
import io.stargate.sgv2.docsapi.service.query.FilterExpression;
import io.stargate.sgv2.docsapi.service.query.FilterPath;
import io.stargate.sgv2.docsapi.service.query.condition.impl.ContainsCondition;
import io.stargate.sgv2.docsapi.service.query.search.db.AbstractSearchQueryBuilder;
import io.stargate.sgv2.docsapi.service.util.DocsApiUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Specialized query builder for CONTAINS operations that generates path-aware queries. This builder
 * ensures that CONTAINS queries only match values within the specified array field.
 */
public class ContainsFilterExpressionSearchQueryBuilder extends AbstractSearchQueryBuilder {

  private final FilterExpression expression;
  private final FilterPath filterPath;

  public ContainsFilterExpressionSearchQueryBuilder(
      DocumentProperties documentProperties, FilterExpression expression) {
    super(documentProperties);

    // Validate that this is indeed a CONTAINS expression
    if (!(expression.getCondition() instanceof ContainsCondition)) {
      throw new IllegalArgumentException(
          "ContainsFilterExpressionSearchQueryBuilder only accepts CONTAINS conditions");
    }

    this.expression = expression;
    this.filterPath = expression.getFilterPath();
  }

  @Override
  protected boolean allowFiltering() {
    return true;
  }

  @Override
  protected List<BuiltCondition> getPredicates() {
    return resolve().getLeft();
  }

  @Override
  protected List<QueryOuterClass.Value> getValues() {
    return resolve().getRight();
  }

  @Override
  protected List<BuiltCondition> getBindPredicates() {
    return Collections.emptyList();
  }

  /**
   * Generates the predicates and values for a CONTAINS query.
   *
   * <p>For a query like { "tags": { "$contains": "javascript" } }, this generates: - p0 = 'tags'
   * (exact match on array field name) - p1 LIKE '[%]' (matches array indices like [000000]) -
   * string_value = 'javascript' (or appropriate value column)
   */
  private Pair<List<BuiltCondition>, List<QueryOuterClass.Value>> resolve() {
    List<BuiltCondition> predicates = new ArrayList<>();
    List<QueryOuterClass.Value> values = new ArrayList<>();
    DocumentTableProperties tableProps = documentProperties.tableProperties();

    // 1. Add path constraints up to the array field
    List<String> parentPath = filterPath.getParentPath();
    for (int i = 0; i < parentPath.size(); i++) {
      String pathSegment = DocsApiUtils.convertEscapedCharacters(parentPath.get(i));
      predicates.add(BuiltCondition.of(tableProps.pathColumnName(i), Predicate.EQ, Term.marker()));
      values.add(Values.of(pathSegment));
    }

    // 2. Add the array field name constraint
    String arrayFieldName = DocsApiUtils.convertEscapedCharacters(filterPath.getField());
    int arrayFieldIndex = parentPath.size();
    predicates.add(
        BuiltCondition.of(tableProps.pathColumnName(arrayFieldIndex), Predicate.EQ, Term.marker()));
    values.add(Values.of(arrayFieldName));

    // 3. Add array index pattern constraint
    // This matches paths like [000000], [000001], etc.
    int arrayIndexColumn = arrayFieldIndex + 1;
    predicates.add(
        BuiltCondition.of(
            tableProps.pathColumnName(arrayIndexColumn), Predicate.LIKE, Term.marker()));
    values.add(Values.of("[%]"));

    // 4. Ensure no further nested paths (array element should be a leaf)
    int afterArrayColumn = arrayIndexColumn + 1;
    if (afterArrayColumn < documentProperties.maxDepth()) {
      predicates.add(
          BuiltCondition.of(
              tableProps.pathColumnName(afterArrayColumn), Predicate.EQ, Term.marker()));
      values.add(Values.of(""));
    }

    // 5. Add the value constraint from the condition
    expression
        .getCondition()
        .getBuiltCondition()
        .ifPresent(
            builtCondition -> {
              predicates.add(builtCondition.getLeft());
              values.add(builtCondition.getRight());
            });

    return Pair.of(predicates, values);
  }
}
