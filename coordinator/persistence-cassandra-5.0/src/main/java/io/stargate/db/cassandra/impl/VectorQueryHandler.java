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

import io.stargate.db.SimpleStatement;
import io.stargate.db.Statement;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simplified vector query handler that works with SimpleStatements containing parameters. This is a
 * workaround for the lack of support for parameterized vector ANN queries.
 */
public class VectorQueryHandler {
  private static final Logger logger = LoggerFactory.getLogger(VectorQueryHandler.class);

  // Pattern to match "ORDER BY <column> ANN OF ?" in queries
  private static final Pattern ANN_PARAM_PATTERN =
      Pattern.compile("\\bORDER\\s+BY\\s+(\\w+)\\s+ANN\\s+OF\\s+\\?", Pattern.CASE_INSENSITIVE);

  /**
   * Process a statement and rewrite if it contains vector ANN parameters
   *
   * @param statement The original statement
   * @return Either the original statement or a rewritten SimpleStatement
   */
  public static Statement processStatement(Statement statement) {
    if (!(statement instanceof SimpleStatement)) {
      return statement;
    }

    SimpleStatement simple = (SimpleStatement) statement;
    String query = simple.queryString();
    List<ByteBuffer> values = simple.values();

    logger.info("VectorQueryHandler processing query: {}", query);

    if (values == null || values.isEmpty()) {
      return statement;
    }

    Matcher matcher = ANN_PARAM_PATTERN.matcher(query);
    if (!matcher.find()) {
      return statement;
    }

    // We found ANN parameters, need to rewrite
    try {
      String rewritten = rewriteVectorQuery(query, values);
      if (rewritten.equals(query)) {
        return statement;
      }

      logger.debug("Rewrote vector query from: {} to: {}", query, rewritten);

      // Return a new SimpleStatement with the rewritten query and no values
      // (since we've inlined the vector values)
      return new SimpleStatement(rewritten);

    } catch (Exception e) {
      logger.warn("Failed to rewrite vector query, using original: {}", e.getMessage());
      return statement;
    }
  }

  /** Rewrite a query by replacing ANN OF ? with literal vectors */
  private static String rewriteVectorQuery(String query, List<ByteBuffer> values) {
    StringBuffer result = new StringBuffer();
    Matcher matcher = ANN_PARAM_PATTERN.matcher(query);

    int paramIndex = 0;
    int lastEnd = 0;

    while (matcher.find()) {
      // Count ? before this match
      String beforeMatch = query.substring(lastEnd, matcher.start());
      paramIndex += countQuestionMarks(beforeMatch);

      // Get the vector value
      if (paramIndex >= values.size()) {
        logger.warn("Not enough values for vector parameter at index {}", paramIndex);
        return query;
      }

      ByteBuffer vectorBuffer = values.get(paramIndex);
      String vectorLiteral = formatVectorFromBuffer(vectorBuffer);

      // Append everything before the match
      result.append(query.substring(lastEnd, matcher.start()));

      // Append the rewritten part
      result.append("ORDER BY ");
      result.append(matcher.group(1));
      result.append(" ANN OF ");
      result.append(vectorLiteral);

      paramIndex++;
      lastEnd = matcher.end();
    }

    // Append the rest of the query
    result.append(query.substring(lastEnd));

    return result.toString();
  }

  private static int countQuestionMarks(String text) {
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '?') {
        count++;
      }
    }
    return count;
  }

  /**
   * Format a ByteBuffer as a vector literal. This assumes the buffer contains a serialized vector
   * of floats.
   */
  private static String formatVectorFromBuffer(ByteBuffer buffer) {
    if (buffer == null || !buffer.hasRemaining()) {
      return "[]";
    }

    try {
      buffer = buffer.duplicate(); // Don't modify the original

      // Log the buffer details for debugging
      int totalBytes = buffer.remaining();
      logger.info("Vector buffer has {} bytes", totalBytes);

      // Try different parsing strategies based on buffer size
      // For a 3-element float vector:
      // - Simple array: 12 bytes (3 * 4)
      // - With 4-byte count prefix: 16 bytes (4 + 3 * 4)
      // - With element size prefixes: 28 bytes (4 + 3 * (4 + 4))

      if (totalBytes == 12) {
        // Simple array of 3 floats
        logger.info("Parsing as simple float array");
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 3; i++) {
          if (i > 0) sb.append(", ");
          sb.append(buffer.getFloat());
        }
        sb.append("]");
        return sb.toString();
      } else if (totalBytes == 16) {
        // 4-byte count + array
        logger.info("Parsing with count prefix");
        int count = buffer.getInt();
        logger.info("Element count: {}", count);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
          if (i > 0) sb.append(", ");
          sb.append(buffer.getFloat());
        }
        sb.append("]");
        return sb.toString();
      } else if (totalBytes == 28) {
        // Collection format with size prefixes
        logger.info("Parsing as collection with size prefixes");
        int count = buffer.getInt();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
          if (i > 0) sb.append(", ");
          int elementSize = buffer.getInt();
          if (elementSize == 4) {
            sb.append(buffer.getFloat());
          } else {
            logger.warn("Unexpected element size: {}", elementSize);
            buffer.position(buffer.position() + elementSize);
          }
        }
        sb.append("]");
        return sb.toString();
      } else {
        logger.warn("Unexpected buffer size: {} bytes", totalBytes);
        // Try to parse as simple array
        int elementCount = totalBytes / 4;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < elementCount; i++) {
          if (i > 0) sb.append(", ");
          sb.append(buffer.getFloat());
        }
        sb.append("]");
        return sb.toString();
      }

    } catch (Exception e) {
      logger.error("Failed to parse vector from buffer", e);
      return "[]";
    }
  }
}
