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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.stargate.sgv2.docsapi.api.v2.namespaces.collections.documents;

import static io.restassured.RestAssured.given;
import static io.stargate.sgv2.common.IntegrationTestUtils.getAuthToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.stargate.sgv2.api.common.config.constants.HttpConstants;
import io.stargate.sgv2.common.testresource.StargateTestResource;
import io.stargate.sgv2.docsapi.api.v2.DocsApiIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@WithTestResource(value = StargateTestResource.class, restrictToAnnotatedClass = false)
class VectorSearchResourceIntegrationTest extends DocsApiIntegrationTest {

  public static final String BASE_PATH = "/v2/namespaces/{namespace}/collections/{collection}";
  public static final String VECTOR_SEARCH_PATH = BASE_PATH + "/vector-search";
  public static final String DEFAULT_NAMESPACE = RandomStringUtils.randomAlphanumeric(16);
  public static final String DEFAULT_COLLECTION = RandomStringUtils.randomAlphanumeric(16);

  final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Optional<String> createNamespace() {
    return Optional.of(DEFAULT_NAMESPACE);
  }

  @Override
  public Optional<String> createCollection() {
    return Optional.of(DEFAULT_COLLECTION);
  }

  @Nested
  class VectorSearch {

    @BeforeEach
    public void setupVectorDocuments() {
      // Insert documents with vector embeddings
      // Document 1: About cats
      String doc1 =
          """
          {
            "id": "doc1",
            "text": "Cats are independent pets",
            "embedding": [0.1, 0.2, 0.3, 0.4, 0.5]
          }
          """;

      // Document 2: About dogs
      String doc2 =
          """
          {
            "id": "doc2",
            "text": "Dogs are loyal companions",
            "embedding": [0.2, 0.3, 0.4, 0.5, 0.6]
          }
          """;

      // Document 3: About cats (similar to doc1)
      String doc3 =
          """
          {
            "id": "doc3",
            "text": "Felines are solitary creatures",
            "embedding": [0.15, 0.25, 0.35, 0.45, 0.55]
          }
          """;

      // Insert all documents
      insertDocument("doc1", doc1);
      insertDocument("doc2", doc2);
      insertDocument("doc3", doc3);
    }

    private void insertDocument(String docId, String document) {
      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(document)
          .when()
          .put(BASE_PATH + "/{docId}", DEFAULT_NAMESPACE, DEFAULT_COLLECTION, docId)
          .then()
          .statusCode(200);
    }

    @Test
    public void vectorSearchBasic() {
      // Search with a vector similar to cat documents
      String searchRequest =
          """
          {
            "vector": [0.12, 0.22, 0.32, 0.42, 0.52],
            "limit": 2
          }
          """;

      Response response =
          given()
              .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
              .contentType(ContentType.JSON)
              .body(searchRequest)
              .when()
              .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
              .then()
              .statusCode(200)
              .body("data", hasSize(2))
              .extract()
              .response();

      // Verify we get full documents back
      List<Map<String, Object>> documents = response.jsonPath().getList("data");
      assertThat(documents).hasSize(2);

      // Check that documents contain the expected fields
      for (Map<String, Object> doc : documents) {
        assertThat(doc).containsKeys("id", "text", "embedding");
        String id = (String) doc.get("id");
        assertThat(id).isIn("doc1", "doc3"); // Should be cat documents
      }
    }

    @Test
    public void vectorSearchWithLimit() {
      String searchRequest =
          """
          {
            "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
            "limit": 1
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(searchRequest)
          .when()
          .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(200)
          .body("data", hasSize(1));
    }

    @Test
    public void vectorSearchWithProfile() {
      String searchRequest =
          """
          {
            "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
            "limit": 10
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .queryParam("profile", true)
          .body(searchRequest)
          .when()
          .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(200)
          .body("profile", notNullValue())
          .body("profile.description", is("root"))
          .body("data", notNullValue());
    }

    @Test
    public void vectorSearchEmptyCollection() {
      String emptyCollection = RandomStringUtils.randomAlphanumeric(16);

      // Create empty collection
      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .when()
          .post("/v2/namespaces/{namespace}/collections", DEFAULT_NAMESPACE)
          .then()
          .statusCode(201);

      String searchRequest =
          """
          {
            "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
            "limit": 10
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(searchRequest)
          .when()
          .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, emptyCollection)
          .then()
          .statusCode(200)
          .body("data", hasSize(0));
    }

    @Test
    public void vectorSearchInvalidDimension() {
      // Test with wrong vector dimension (default is 1536, but our test uses 5)
      String searchRequest =
          """
          {
            "vector": [0.1, 0.2],
            "limit": 10
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(searchRequest)
          .when()
          .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body("description", containsString("Vector dimension mismatch"));
    }

    @Test
    public void vectorSearchMissingVector() {
      String searchRequest =
          """
          {
            "limit": 10
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(searchRequest)
          .when()
          .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(400);
    }

    @Test
    public void vectorSearchInvalidLimit() {
      String searchRequest =
          """
          {
            "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
            "limit": 0
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(searchRequest)
          .when()
          .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(400);
    }

    @Test
    public void vectorSearchExceedsMaxLimit() {
      String searchRequest =
          """
          {
            "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
            "limit": 1001
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(searchRequest)
          .when()
          .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
          .then()
          .statusCode(400);
    }

    @Test
    public void vectorSearchNonexistentCollection() {
      String searchRequest =
          """
          {
            "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
            "limit": 10
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(searchRequest)
          .when()
          .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, "nonexistent")
          .then()
          .statusCode(404)
          .body("code", equalTo(404));
    }

    @Test
    public void vectorSearchNonexistentNamespace() {
      String searchRequest =
          """
          {
            "vector": [0.1, 0.2, 0.3, 0.4, 0.5],
            "limit": 10
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(searchRequest)
          .when()
          .post(VECTOR_SEARCH_PATH, "nonexistent", DEFAULT_COLLECTION)
          .then()
          .statusCode(404)
          .body("code", equalTo(404));
    }

    @Test
    public void vectorSearchWithNonNumericArray() {
      // Test that non-numeric arrays are not treated as vectors
      String doc =
          """
          {
            "id": "doc_string_array",
            "tags": ["cat", "pet", "animal"],
            "embedding": [0.7, 0.8, 0.9, 1.0, 1.1]
          }
          """;

      insertDocument("doc_string_array", doc);

      // Search should still work and not be confused by string array
      String searchRequest =
          """
          {
            "vector": [0.7, 0.8, 0.9, 1.0, 1.1],
            "limit": 5
          }
          """;

      Response response =
          given()
              .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
              .contentType(ContentType.JSON)
              .body(searchRequest)
              .when()
              .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
              .then()
              .statusCode(200)
              .extract()
              .response();

      // Should find the document with matching vector
      List<String> results = response.jsonPath().getList("data");
      assertThat(results).contains("doc_string_array");
    }
  }
}
