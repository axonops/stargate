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
package io.stargate.sgv2.docsapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JsonDocumentShredderVectorTest {

  @Inject JsonDocumentShredder shredder;

  @Test
  public void shredVectorArray() throws Exception {
    String json =
        """
        {
          "id": "doc1",
          "embedding": [0.1, 0.2, 0.3, 0.4, 0.5]
        }
        """;

    List<JsonShreddedRow> rows = shredder.shred(json, Collections.emptyList());

    // Should have 2 rows: one for "id" and one for "embedding"
    assertThat(rows).hasSize(2);

    // Find the embedding row
    JsonShreddedRow embeddingRow =
        rows.stream().filter(row -> row.getPath().contains("embedding")).findFirst().orElseThrow();

    // Verify it's stored as a vector
    assertThat(embeddingRow.getVectorValue()).isNotNull();
    assertThat(embeddingRow.getVectorValue()).hasSize(5);
    assertThat(embeddingRow.getVectorValue()).containsExactly(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);

    // Other value types should be null
    assertThat(embeddingRow.getStringValue()).isNull();
    assertThat(embeddingRow.getDoubleValue()).isNull();
    assertThat(embeddingRow.getBooleanValue()).isNull();
  }

  @Test
  public void shredNestedVectorArray() throws Exception {
    String json =
        """
        {
          "document": {
            "vector": [1.0, 2.0, 3.0]
          }
        }
        """;

    List<JsonShreddedRow> rows = shredder.shred(json, Collections.emptyList());

    // Find the vector row
    JsonShreddedRow vectorRow =
        rows.stream()
            .filter(row -> row.getPath().size() == 2 && row.getPath().get(1).equals("vector"))
            .findFirst()
            .orElseThrow();

    assertThat(vectorRow.getVectorValue()).isNotNull();
    assertThat(vectorRow.getVectorValue()).containsExactly(1.0f, 2.0f, 3.0f);
  }

  @Test
  public void shredMixedArray_NotVector() throws Exception {
    // Arrays with mixed types should not be treated as vectors
    String json =
        """
        {
          "mixed": [1, "two", 3.0, true]
        }
        """;

    List<JsonShreddedRow> rows = shredder.shred(json, Collections.emptyList());

    // Should have rows for each array element, not a single vector
    long arrayElementRows =
        rows.stream()
            .filter(row -> row.getPath().size() == 2 && row.getPath().get(0).equals("mixed"))
            .count();

    assertThat(arrayElementRows).isEqualTo(4); // 4 elements in the array

    // No row should have a vector value
    assertThat(rows).noneMatch(row -> row.getVectorValue() != null);
  }

  @Test
  public void shredStringArray_NotVector() throws Exception {
    // String arrays should not be treated as vectors
    String json =
        """
        {
          "tags": ["cat", "pet", "animal"]
        }
        """;

    List<JsonShreddedRow> rows = shredder.shred(json, Collections.emptyList());

    // Should have rows for each array element
    long stringRows =
        rows.stream()
            .filter(row -> row.getPath().size() == 2 && row.getPath().get(0).equals("tags"))
            .filter(row -> row.getStringValue() != null)
            .count();

    assertThat(stringRows).isEqualTo(3);

    // No vector values
    assertThat(rows).noneMatch(row -> row.getVectorValue() != null);
  }

  @Test
  public void shredEmptyArray() throws Exception {
    String json =
        """
        {
          "empty": []
        }
        """;

    List<JsonShreddedRow> rows = shredder.shred(json, Collections.emptyList());

    // Empty arrays are stored with special marker
    JsonShreddedRow emptyRow =
        rows.stream().filter(row -> row.getPath().contains("empty")).findFirst().orElseThrow();

    assertThat(emptyRow.getStringValue()).isEqualTo("AAEMPTY_ARRAYAA");
    assertThat(emptyRow.getVectorValue()).isNull();
  }

  @Test
  public void shredLargeVector() throws Exception {
    // Test with a larger vector (e.g., 1536 dimensions like OpenAI embeddings)
    float[] largeVector = new float[1536];
    for (int i = 0; i < largeVector.length; i++) {
      largeVector[i] = i * 0.001f;
    }

    StringBuilder jsonBuilder = new StringBuilder("{\"embedding\": [");
    for (int i = 0; i < largeVector.length; i++) {
      if (i > 0) jsonBuilder.append(", ");
      jsonBuilder.append(largeVector[i]);
    }
    jsonBuilder.append("]}");

    List<JsonShreddedRow> rows = shredder.shred(jsonBuilder.toString(), Collections.emptyList());

    JsonShreddedRow vectorRow =
        rows.stream().filter(row -> row.getPath().contains("embedding")).findFirst().orElseThrow();

    assertThat(vectorRow.getVectorValue()).isNotNull();
    assertThat(vectorRow.getVectorValue()).hasSize(1536);
    assertThat(vectorRow.getVectorValue()[0]).isEqualTo(0.0f);
    assertThat(vectorRow.getVectorValue()[1535]).isEqualTo(1.535f);
  }

  @Test
  public void shredMultipleVectors() throws Exception {
    String json =
        """
        {
          "vector1": [1.0, 2.0],
          "vector2": [3.0, 4.0],
          "data": {
            "vector3": [5.0, 6.0]
          }
        }
        """;

    List<JsonShreddedRow> rows = shredder.shred(json, Collections.emptyList());

    // Count vector rows
    long vectorRows = rows.stream().filter(row -> row.getVectorValue() != null).count();

    assertThat(vectorRows).isEqualTo(3);

    // Verify each vector
    JsonShreddedRow vector1 = findRowByPath(rows, "vector1");
    assertThat(vector1.getVectorValue()).containsExactly(1.0f, 2.0f);

    JsonShreddedRow vector2 = findRowByPath(rows, "vector2");
    assertThat(vector2.getVectorValue()).containsExactly(3.0f, 4.0f);

    JsonShreddedRow vector3 = findRowByLastPath(rows, "vector3");
    assertThat(vector3.getVectorValue()).containsExactly(5.0f, 6.0f);
  }

  @Test
  public void shredIntegerArray_AsVector() throws Exception {
    // Integer arrays should also be treated as vectors
    String json =
        """
        {
          "intVector": [1, 2, 3, 4, 5]
        }
        """;

    List<JsonShreddedRow> rows = shredder.shred(json, Collections.emptyList());

    JsonShreddedRow vectorRow =
        rows.stream().filter(row -> row.getPath().contains("intVector")).findFirst().orElseThrow();

    assertThat(vectorRow.getVectorValue()).isNotNull();
    assertThat(vectorRow.getVectorValue()).containsExactly(1.0f, 2.0f, 3.0f, 4.0f, 5.0f);
  }

  @Test
  public void shredNegativeAndZeroValues() throws Exception {
    String json =
        """
        {
          "vector": [-1.5, 0, 0.0, -0.0, 2.5]
        }
        """;

    List<JsonShreddedRow> rows = shredder.shred(json, Collections.emptyList());

    JsonShreddedRow vectorRow =
        rows.stream().filter(row -> row.getPath().contains("vector")).findFirst().orElseThrow();

    assertThat(vectorRow.getVectorValue()).isNotNull();
    assertThat(vectorRow.getVectorValue()).containsExactly(-1.5f, 0.0f, 0.0f, -0.0f, 2.5f);
  }

  private JsonShreddedRow findRowByPath(List<JsonShreddedRow> rows, String pathElement) {
    return rows.stream()
        .filter(row -> row.getPath().size() == 1 && row.getPath().get(0).equals(pathElement))
        .findFirst()
        .orElseThrow();
  }

  private JsonShreddedRow findRowByLastPath(List<JsonShreddedRow> rows, String lastPathElement) {
    return rows.stream()
        .filter(
            row ->
                !row.getPath().isEmpty()
                    && row.getPath().get(row.getPath().size() - 1).equals(lastPathElement))
        .findFirst()
        .orElseThrow();
  }
}
