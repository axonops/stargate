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
package io.stargate.sgv2.docsapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Simple standalone test to verify vector JSON processing works correctly. This can be run as a
 * main method without any test framework.
 */
public class SimpleVectorTest {

  private static final ObjectMapper mapper = new ObjectMapper();

  public static void main(String[] args) {
    System.out.println("=== Simple Vector Processing Test ===\n");

    try {
      testVectorJsonCreation();
      testVectorArrayDetection();
      testVectorSerialization();
      testLargeVector();

      System.out.println("\n✅ All tests passed!");
    } catch (Exception e) {
      System.err.println("\n❌ Test failed: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static void testVectorJsonCreation() throws Exception {
    System.out.println("1. Testing vector JSON creation...");

    ObjectNode doc = mapper.createObjectNode();
    doc.put("id", "doc1");
    doc.put("text", "This is a test document");

    // Add a vector as an array
    ArrayNode vector = mapper.createArrayNode();
    vector.add(0.1).add(0.2).add(0.3).add(0.4).add(0.5);
    doc.set("embedding", vector);

    String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc);
    System.out.println("Created document:");
    System.out.println(json);

    // Verify
    assert doc.get("embedding").isArray();
    assert doc.get("embedding").size() == 5;
    assert doc.get("embedding").get(0).asDouble() == 0.1;

    System.out.println("✓ Vector JSON creation successful\n");
  }

  private static void testVectorArrayDetection() throws Exception {
    System.out.println("2. Testing vector array detection...");

    // Test numeric array (should be detected as vector)
    String numericArrayJson = "[0.1, 0.2, 0.3]";
    ArrayNode numericArray = (ArrayNode) mapper.readTree(numericArrayJson);

    boolean isNumericVector = isVectorArray(numericArray);
    System.out.println("Numeric array [0.1, 0.2, 0.3] is vector: " + isNumericVector);
    assert isNumericVector;

    // Test string array (should NOT be detected as vector)
    String stringArrayJson = "[\"cat\", \"dog\", \"bird\"]";
    ArrayNode stringArray = (ArrayNode) mapper.readTree(stringArrayJson);

    boolean isStringVector = isVectorArray(stringArray);
    System.out.println("String array [\"cat\", \"dog\", \"bird\"] is vector: " + isStringVector);
    assert !isStringVector;

    // Test mixed array (should NOT be detected as vector)
    String mixedArrayJson = "[0.1, \"text\", 0.3]";
    ArrayNode mixedArray = (ArrayNode) mapper.readTree(mixedArrayJson);

    boolean isMixedVector = isVectorArray(mixedArray);
    System.out.println("Mixed array [0.1, \"text\", 0.3] is vector: " + isMixedVector);
    assert !isMixedVector;

    System.out.println("✓ Vector array detection successful\n");
  }

  private static void testVectorSerialization() throws Exception {
    System.out.println("3. Testing vector serialization...");

    // Create a float array
    float[] vectorData = new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};

    // Convert to JSON
    ArrayNode vectorNode = mapper.createArrayNode();
    for (float value : vectorData) {
      vectorNode.add(value);
    }

    String vectorJson = mapper.writeValueAsString(vectorNode);
    System.out.println("Serialized vector: " + vectorJson);

    // Deserialize back
    ArrayNode deserializedNode = (ArrayNode) mapper.readTree(vectorJson);
    float[] deserializedVector = new float[deserializedNode.size()];
    for (int i = 0; i < deserializedNode.size(); i++) {
      deserializedVector[i] = (float) deserializedNode.get(i).asDouble();
    }

    // Verify
    assert deserializedVector.length == vectorData.length;
    for (int i = 0; i < vectorData.length; i++) {
      assert Math.abs(deserializedVector[i] - vectorData[i]) < 0.0001;
    }

    System.out.println("✓ Vector serialization successful\n");
  }

  private static void testLargeVector() throws Exception {
    System.out.println("4. Testing large vector (OpenAI dimension)...");

    int dimension = 1536;
    ObjectNode doc = mapper.createObjectNode();
    doc.put("id", "openai-doc");

    // Create large vector
    ArrayNode largeVector = mapper.createArrayNode();
    for (int i = 0; i < dimension; i++) {
      largeVector.add(Math.sin(i * 0.01));
    }
    doc.set("embedding", largeVector);

    // Serialize and check size
    String json = mapper.writeValueAsString(doc);
    System.out.println(
        "Document with " + dimension + "-dimensional vector serialized successfully");
    System.out.println("JSON size: " + json.length() + " characters");

    // Verify
    ObjectNode parsed = (ObjectNode) mapper.readTree(json);
    assert parsed.get("embedding").size() == dimension;

    System.out.println("✓ Large vector handling successful\n");
  }

  // Helper method to detect if an array should be treated as a vector
  private static boolean isVectorArray(ArrayNode array) {
    if (array.isEmpty()) {
      return false;
    }

    // Check if all elements are numeric
    for (int i = 0; i < array.size(); i++) {
      if (!array.get(i).isNumber()) {
        return false;
      }
    }

    return true;
  }
}
