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

import static org.assertj.core.api.Assertions.assertThat;

import io.stargate.db.SimpleStatement;
import io.stargate.db.Statement;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class VectorQueryHandlerTest {

  @Test
  public void testNonVectorQuery() {
    String query = "SELECT * FROM products WHERE id = ?";
    ByteBuffer value = ByteBuffer.allocate(4).putInt(1);
    value.flip();

    SimpleStatement stmt = new SimpleStatement(query, Collections.singletonList(value));
    Statement result = VectorQueryHandler.processStatement(stmt);

    assertThat(result).isSameAs(stmt);
  }

  @Test
  public void testVectorQueryWithSimpleArray() {
    String query = "SELECT * FROM products ORDER BY embedding ANN OF ? LIMIT 3";

    // Create a simple array of 3 floats (12 bytes)
    ByteBuffer vectorBuffer = ByteBuffer.allocate(12);
    vectorBuffer.order(ByteOrder.BIG_ENDIAN);
    vectorBuffer.putFloat(0.1f);
    vectorBuffer.putFloat(0.2f);
    vectorBuffer.putFloat(0.3f);
    vectorBuffer.flip();

    SimpleStatement stmt = new SimpleStatement(query, Collections.singletonList(vectorBuffer));
    Statement result = VectorQueryHandler.processStatement(stmt);

    assertThat(result).isInstanceOf(SimpleStatement.class);
    SimpleStatement rewritten = (SimpleStatement) result;
    assertThat(rewritten.queryString())
        .isEqualTo("SELECT * FROM products ORDER BY embedding ANN OF [0.1, 0.2, 0.3] LIMIT 3");
    assertThat(rewritten.values()).isEmpty();
  }

  @Test
  public void testVectorQueryWithCountPrefix() {
    String query = "SELECT * FROM products ORDER BY embedding ANN OF ? LIMIT 3";

    // Create array with 4-byte count prefix (16 bytes total)
    ByteBuffer vectorBuffer = ByteBuffer.allocate(16);
    vectorBuffer.order(ByteOrder.BIG_ENDIAN);
    vectorBuffer.putInt(3); // count
    vectorBuffer.putFloat(0.4f);
    vectorBuffer.putFloat(0.5f);
    vectorBuffer.putFloat(0.6f);
    vectorBuffer.flip();

    SimpleStatement stmt = new SimpleStatement(query, Collections.singletonList(vectorBuffer));
    Statement result = VectorQueryHandler.processStatement(stmt);

    assertThat(result).isInstanceOf(SimpleStatement.class);
    SimpleStatement rewritten = (SimpleStatement) result;
    assertThat(rewritten.queryString())
        .isEqualTo("SELECT * FROM products ORDER BY embedding ANN OF [0.4, 0.5, 0.6] LIMIT 3");
  }

  @Test
  public void testVectorQueryWithCollectionFormat() {
    String query = "SELECT * FROM products ORDER BY embedding ANN OF ? LIMIT 3";

    // Create collection format with size prefixes (28 bytes total)
    ByteBuffer vectorBuffer = ByteBuffer.allocate(28);
    vectorBuffer.order(ByteOrder.BIG_ENDIAN);
    vectorBuffer.putInt(3); // count
    vectorBuffer.putInt(4); // size of first element
    vectorBuffer.putFloat(0.7f);
    vectorBuffer.putInt(4); // size of second element
    vectorBuffer.putFloat(0.8f);
    vectorBuffer.putInt(4); // size of third element
    vectorBuffer.putFloat(0.9f);
    vectorBuffer.flip();

    SimpleStatement stmt = new SimpleStatement(query, Collections.singletonList(vectorBuffer));
    Statement result = VectorQueryHandler.processStatement(stmt);

    assertThat(result).isInstanceOf(SimpleStatement.class);
    SimpleStatement rewritten = (SimpleStatement) result;
    assertThat(rewritten.queryString())
        .isEqualTo("SELECT * FROM products ORDER BY embedding ANN OF [0.7, 0.8, 0.9] LIMIT 3");
  }

  @Test
  public void testMultipleParametersBeforeVector() {
    String query =
        "SELECT * FROM products WHERE category = ? AND price > ? ORDER BY embedding ANN OF ? LIMIT 3";

    ByteBuffer categoryBuffer = ByteBuffer.wrap("electronics".getBytes());
    ByteBuffer priceBuffer = ByteBuffer.allocate(8).putDouble(100.0);
    priceBuffer.flip();

    ByteBuffer vectorBuffer = ByteBuffer.allocate(12);
    vectorBuffer.order(ByteOrder.BIG_ENDIAN);
    vectorBuffer.putFloat(0.1f);
    vectorBuffer.putFloat(0.2f);
    vectorBuffer.putFloat(0.3f);
    vectorBuffer.flip();

    SimpleStatement stmt =
        new SimpleStatement(query, Arrays.asList(categoryBuffer, priceBuffer, vectorBuffer));
    Statement result = VectorQueryHandler.processStatement(stmt);

    assertThat(result).isInstanceOf(SimpleStatement.class);
    SimpleStatement rewritten = (SimpleStatement) result;
    assertThat(rewritten.queryString())
        .isEqualTo(
            "SELECT * FROM products WHERE category = ? AND price > ? ORDER BY embedding ANN OF [0.1, 0.2, 0.3] LIMIT 3");
  }

  @Test
  public void testCaseInsensitiveMatching() {
    String query = "SELECT * FROM products order by embedding ann of ? LIMIT 3";

    ByteBuffer vectorBuffer = ByteBuffer.allocate(12);
    vectorBuffer.order(ByteOrder.BIG_ENDIAN);
    vectorBuffer.putFloat(0.1f);
    vectorBuffer.putFloat(0.2f);
    vectorBuffer.putFloat(0.3f);
    vectorBuffer.flip();

    SimpleStatement stmt = new SimpleStatement(query, Collections.singletonList(vectorBuffer));
    Statement result = VectorQueryHandler.processStatement(stmt);

    assertThat(result).isInstanceOf(SimpleStatement.class);
    SimpleStatement rewritten = (SimpleStatement) result;
    assertThat(rewritten.queryString())
        .isEqualTo("SELECT * FROM products ORDER BY embedding ANN OF [0.1, 0.2, 0.3] LIMIT 3");
  }

  @Test
  public void testEmptyVector() {
    String query = "SELECT * FROM products ORDER BY embedding ANN OF ? LIMIT 3";

    ByteBuffer vectorBuffer = ByteBuffer.allocate(0);
    SimpleStatement stmt = new SimpleStatement(query, Collections.singletonList(vectorBuffer));
    Statement result = VectorQueryHandler.processStatement(stmt);

    assertThat(result).isInstanceOf(SimpleStatement.class);
    SimpleStatement rewritten = (SimpleStatement) result;
    assertThat(rewritten.queryString())
        .isEqualTo("SELECT * FROM products ORDER BY embedding ANN OF [] LIMIT 3");
  }
}
