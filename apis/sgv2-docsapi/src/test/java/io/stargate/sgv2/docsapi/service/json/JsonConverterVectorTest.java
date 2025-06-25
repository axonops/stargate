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
package io.stargate.sgv2.docsapi.service.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentProperties;
import io.stargate.sgv2.docsapi.api.properties.document.DocumentTableProperties;
import io.stargate.sgv2.docsapi.service.common.model.RowWrapper;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JsonConverterVectorTest {

  @Inject JsonConverter jsonConverter;
  @Inject DocumentProperties documentProperties;

  private DocumentTableProperties tableProperties;

  @BeforeEach
  void setUp() {
    tableProperties = documentProperties.tableProperties();
  }

  @Test
  void convertToJsonDoc_WithVector() throws Exception {
    // Create mock rows representing a document with a vector
    List<RowWrapper> rows = new ArrayList<>();

    // Row for a simple string field
    RowWrapper textRow =
        createMockRow("key1", new String[] {"text"}, "text", "Sample text", null, null, null, 100L);
    rows.add(textRow);

    // Row for a vector field
    float[] vector = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
    RowWrapper vectorRow =
        createMockRow(
            "key1", new String[] {"embedding"}, "embedding", null, null, null, vector, 100L);
    rows.add(vectorRow);

    // Convert to JSON
    JsonNode result = jsonConverter.convertToJsonDoc(rows, false, false);

    // Verify structure
    assertThat(result.isObject()).isTrue();
    assertThat(result.has("text")).isTrue();
    assertThat(result.get("text").asText()).isEqualTo("Sample text");

    // Verify vector is converted to array
    assertThat(result.has("embedding")).isTrue();
    JsonNode embeddingNode = result.get("embedding");
    assertThat(embeddingNode.isArray()).isTrue();
    assertThat(embeddingNode.size()).isEqualTo(5);

    // Verify vector values
    for (int i = 0; i < vector.length; i++) {
      assertThat(embeddingNode.get(i).floatValue()).isEqualTo(vector[i]);
    }
  }

  @Test
  void convertToJsonDoc_NestedVector() throws Exception {
    List<RowWrapper> rows = new ArrayList<>();

    // Nested vector field
    float[] vector = {1.0f, 2.0f, 3.0f};
    RowWrapper vectorRow =
        createMockRow(
            "key1", new String[] {"data", "vector"}, "vector", null, null, null, vector, 100L);
    rows.add(vectorRow);

    // Convert to JSON
    JsonNode result = jsonConverter.convertToJsonDoc(rows, false, false);

    // Verify nested structure
    assertThat(result.has("data")).isTrue();
    assertThat(result.get("data").has("vector")).isTrue();

    JsonNode vectorNode = result.get("data").get("vector");
    assertThat(vectorNode.isArray()).isTrue();
    assertThat(vectorNode.size()).isEqualTo(3);
    assertThat(vectorNode.get(0).floatValue()).isEqualTo(1.0f);
    assertThat(vectorNode.get(1).floatValue()).isEqualTo(2.0f);
    assertThat(vectorNode.get(2).floatValue()).isEqualTo(3.0f);
  }

  @Test
  void convertToJsonDoc_MultipleVectors() throws Exception {
    List<RowWrapper> rows = new ArrayList<>();

    // First vector
    float[] vector1 = {0.1f, 0.2f};
    rows.add(createMockRow("key1", new String[] {"vec1"}, "vec1", null, null, null, vector1, 100L));

    // Second vector
    float[] vector2 = {0.3f, 0.4f};
    rows.add(createMockRow("key1", new String[] {"vec2"}, "vec2", null, null, null, vector2, 100L));

    // Regular field
    rows.add(createMockRow("key1", new String[] {"name"}, "name", "test", null, null, null, 100L));

    // Convert to JSON
    JsonNode result = jsonConverter.convertToJsonDoc(rows, false, false);

    // Verify all fields
    assertThat(result.has("vec1")).isTrue();
    assertThat(result.has("vec2")).isTrue();
    assertThat(result.has("name")).isTrue();

    // Verify vectors
    JsonNode vec1Node = result.get("vec1");
    assertThat(vec1Node.isArray()).isTrue();
    assertThat(vec1Node.size()).isEqualTo(2);

    JsonNode vec2Node = result.get("vec2");
    assertThat(vec2Node.isArray()).isTrue();
    assertThat(vec2Node.size()).isEqualTo(2);
  }

  @Test
  void convertToJsonDoc_EmptyVector() throws Exception {
    List<RowWrapper> rows = new ArrayList<>();

    // Empty vector (though this shouldn't happen in practice)
    float[] vector = new float[0];
    rows.add(
        createMockRow("key1", new String[] {"empty"}, "empty", null, null, null, vector, 100L));

    // Convert to JSON
    JsonNode result = jsonConverter.convertToJsonDoc(rows, false, false);

    // Verify empty array
    assertThat(result.has("empty")).isTrue();
    JsonNode emptyNode = result.get("empty");
    assertThat(emptyNode.isArray()).isTrue();
    assertThat(emptyNode.size()).isEqualTo(0);
  }

  @Test
  void convertToJsonDoc_MixedDocument() throws Exception {
    List<RowWrapper> rows = new ArrayList<>();

    // Mix of different value types including vector
    rows.add(createMockRow("key1", new String[] {"text"}, "text", "hello", null, null, null, 100L));
    rows.add(
        createMockRow("key1", new String[] {"number"}, "number", null, 42.5, null, null, 100L));
    rows.add(createMockRow("key1", new String[] {"flag"}, "flag", null, null, true, null, 100L));

    float[] vector = {1.1f, 2.2f, 3.3f};
    rows.add(
        createMockRow("key1", new String[] {"coords"}, "coords", null, null, null, vector, 100L));

    // Convert to JSON
    JsonNode result = jsonConverter.convertToJsonDoc(rows, false, false);

    // Verify all fields exist with correct types
    assertThat(result.get("text").isTextual()).isTrue();
    assertThat(result.get("number").isNumber()).isTrue();
    assertThat(result.get("flag").isBoolean()).isTrue();
    assertThat(result.get("coords").isArray()).isTrue();

    // Verify values
    assertThat(result.get("text").asText()).isEqualTo("hello");
    assertThat(result.get("number").asDouble()).isEqualTo(42.5);
    assertThat(result.get("flag").asBoolean()).isTrue();
    assertThat(result.get("coords").size()).isEqualTo(3);
  }

  private RowWrapper createMockRow(
      String key,
      String[] path,
      String leaf,
      String stringValue,
      Double doubleValue,
      Boolean booleanValue,
      float[] vectorValue,
      long writetime) {

    RowWrapper row = mock(RowWrapper.class);

    // Mock key
    when(row.getString(tableProperties.keyColumnName())).thenReturn(key);

    // Mock path columns
    for (int i = 0; i < documentProperties.maxDepth(); i++) {
      String pathCol = tableProperties.pathColumnName(i);
      if (i < path.length) {
        when(row.getString(pathCol)).thenReturn(path[i]);
      } else {
        when(row.getString(pathCol)).thenReturn("");
      }
    }

    // Mock leaf
    when(row.getString(tableProperties.leafColumnName())).thenReturn(leaf);

    // Mock values
    when(row.isNull(tableProperties.stringValueColumnName())).thenReturn(stringValue == null);
    when(row.getString(tableProperties.stringValueColumnName())).thenReturn(stringValue);

    when(row.isNull(tableProperties.doubleValueColumnName())).thenReturn(doubleValue == null);
    when(row.getDouble(tableProperties.doubleValueColumnName())).thenReturn(doubleValue);

    when(row.isNull(tableProperties.booleanValueColumnName())).thenReturn(booleanValue == null);
    when(row.getBoolean(tableProperties.booleanValueColumnName())).thenReturn(booleanValue);

    when(row.isNull(tableProperties.vectorValueColumnName())).thenReturn(vectorValue == null);
    when(row.getVector(tableProperties.vectorValueColumnName())).thenReturn(vectorValue);

    // Mock writetime
    when(row.getLong(tableProperties.writetimeColumnName())).thenReturn(writetime);

    return row;
  }
}
