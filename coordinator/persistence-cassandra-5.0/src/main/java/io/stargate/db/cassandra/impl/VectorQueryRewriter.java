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
package io.stargate.db.cassandra.impl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.ListType;
import org.apache.cassandra.db.marshal.VectorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to handle query rewriting for vector ANN (Approximate Nearest Neighbor) queries.
 *
 * <p>This class works around the limitation where parameterized vector queries don't work with the
 * "ORDER BY column ANN OF ?" syntax by rewriting them to use literal vectors.
 */
public class VectorQueryRewriter {
  private static final Logger logger = LoggerFactory.getLogger(VectorQueryRewriter.class);

  // Pattern to match "ORDER BY <column> ANN OF ?" in queries
  private static final Pattern ANN_PARAM_PATTERN =
      Pattern.compile(
          "\\bORDER\\s+BY\\s+(\\w+)\\s+ANN\\s+OF\\s+\\?",
          Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

  /** Check if a query contains parameterized ANN syntax */
  public static boolean hasParameterizedANN(String query) {
    return ANN_PARAM_PATTERN.matcher(query).find();
  }

  /**
   * Rewrite a query with parameterized ANN syntax, replacing ? with literal vectors
   *
   * @param query The original query with "ORDER BY column ANN OF ?"
   * @param values The bound values for the query
   * @param types The types of the bound values (null if not available)
   * @return RewriteResult containing the rewritten query and adjusted values
   */
  public static RewriteResult rewriteQuery(
      String query, List<ByteBuffer> values, List<AbstractType<?>> types) {
    Matcher matcher = ANN_PARAM_PATTERN.matcher(query);

    if (!matcher.find()) {
      // No ANN parameters found, return original
      return new RewriteResult(query, values);
    }

    // Track which parameter positions are ANN vectors
    List<Integer> annParamPositions = new ArrayList<>();
    StringBuffer rewritten = new StringBuffer();
    int paramIndex = 0;
    int lastEnd = 0;

    // Count parameters before each ANN match to determine position
    matcher.reset();
    while (matcher.find()) {
      String beforeMatch = query.substring(lastEnd, matcher.start());
      int paramsBefore = countParameters(beforeMatch);
      paramIndex += paramsBefore;

      if (paramIndex < values.size()) {
        ByteBuffer vectorValue = values.get(paramIndex);
        String vectorLiteral =
            formatVectorLiteral(vectorValue, types != null ? types.get(paramIndex) : null);

        // Append everything before the match
        rewritten.append(query.substring(lastEnd, matcher.start()));
        // Append the rewritten part
        rewritten
            .append("ORDER BY ")
            .append(matcher.group(1))
            .append(" ANN OF ")
            .append(vectorLiteral);

        annParamPositions.add(paramIndex);
        paramIndex++;
      }

      lastEnd = matcher.end();
    }

    // Append remaining query
    rewritten.append(query.substring(lastEnd));

    // Remove vector parameters from values list
    List<ByteBuffer> adjustedValues = new ArrayList<>(values);
    // Remove in reverse order to maintain indices
    for (int i = annParamPositions.size() - 1; i >= 0; i--) {
      adjustedValues.remove((int) annParamPositions.get(i));
    }

    String rewrittenQuery = rewritten.toString();
    logger.debug("Rewrote vector query from: {} to: {}", query, rewrittenQuery);

    return new RewriteResult(rewrittenQuery, adjustedValues);
  }

  /** Count the number of ? parameters in a string */
  private static int countParameters(String text) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf('?', index)) != -1) {
      count++;
      index++;
    }
    return count;
  }

  /** Format a vector value as a CQL literal [x, y, z] */
  private static String formatVectorLiteral(ByteBuffer value, AbstractType<?> type) {
    if (value == null || !value.hasRemaining()) {
      return "[]";
    }

    try {
      List<?> elements;

      if (type instanceof VectorType) {
        VectorType<?> vectorType = (VectorType<?>) type;
        elements = vectorType.getSerializer().deserialize(value);
      } else if (type instanceof ListType) {
        // Fallback for list type
        ListType<?> listType = (ListType<?>) type;
        elements = listType.getSerializer().deserialize(value);
      } else {
        // Try to deserialize as a list of floats
        logger.warn(
            "Unknown type for vector parameter: {}, attempting generic deserialization", type);
        // This is a fallback - in practice we should always have type information
        return "[]";
      }

      if (elements == null || elements.isEmpty()) {
        return "[]";
      }

      // Format as [x, y, z]
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < elements.size(); i++) {
        if (i > 0) sb.append(", ");

        Object elem = elements.get(i);
        if (elem instanceof Float) {
          sb.append(elem);
        } else if (elem instanceof Double) {
          sb.append(elem);
        } else if (elem instanceof Number) {
          sb.append(((Number) elem).floatValue());
        } else {
          sb.append(elem.toString());
        }
      }
      sb.append("]");

      return sb.toString();

    } catch (Exception e) {
      logger.error("Failed to format vector literal from ByteBuffer", e);
      return "[]";
    }
  }

  /** Result of query rewriting */
  public static class RewriteResult {
    public final String query;
    public final List<ByteBuffer> values;

    public RewriteResult(String query, List<ByteBuffer> values) {
      this.query = query;
      this.values = values;
    }
  }
}
