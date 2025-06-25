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
import static org.hamcrest.Matchers.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.stargate.sgv2.api.common.config.constants.HttpConstants;
import io.stargate.sgv2.docsapi.api.v2.DocsApiIntegrationTest;
import io.stargate.sgv2.docsapi.testresource.ExternalCassandraTestResource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test specifically for Cassandra 5.0 vector functionality. This test connects to an
 * external Cassandra 5.0 instance configured via stargate-config.properties.
 *
 * <p>To run this test: 1. Ensure Cassandra 5.0 is running with the configuration specified in
 * stargate-config.properties 2. Ensure Stargate coordinator is running and accessible 3. Run: mvn
 * test -Dtest=Cassandra5VectorIntegrationTest -DfailIfNoTests=false
 *
 * <p>Connection settings can be configured via: - Configuration file: stargate-config.properties -
 * System properties: test.cassandra.host, test.cassandra.port, test.coordinator.host, etc. -
 * Environment variables: TEST_CASSANDRA_HOST, TEST_CASSANDRA_PORT, etc.
 *
 * <p>Default configuration is loaded from stargate-config.properties in the project root.
 */
@QuarkusIntegrationTest
@WithTestResource(value = ExternalCassandraTestResource.class, restrictToAnnotatedClass = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Cassandra5VectorIntegrationTest extends DocsApiIntegrationTest {

  private static final Logger LOG = LoggerFactory.getLogger(Cassandra5VectorIntegrationTest.class);

  public static final String BASE_PATH = "/v2/namespaces/{namespace}/collections/{collection}";
  public static final String VECTOR_SEARCH_PATH = BASE_PATH + "/vector-search";
  public static final String DEFAULT_NAMESPACE =
      "vector_test_" + RandomStringUtils.randomAlphanumeric(8);
  public static final String DEFAULT_COLLECTION =
      "vectors_" + RandomStringUtils.randomAlphanumeric(8);

  // Cassandra 5.0 supports different vector dimensions
  private static final int VECTOR_DIMENSION_SMALL = 3;
  private static final int VECTOR_DIMENSION_STANDARD = 1536; // OpenAI embedding size

  final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Optional<String> createNamespace() {
    return Optional.of(DEFAULT_NAMESPACE);
  }

  @Override
  public Optional<String> createCollection() {
    return Optional.of(DEFAULT_COLLECTION);
  }

  @BeforeAll
  public void logTestSetup() {
    LOG.info("Starting Cassandra 5.0 Vector Integration Test");
    LOG.info("Namespace: {}, Collection: {}", DEFAULT_NAMESPACE, DEFAULT_COLLECTION);
  }

  @Nested
  @DisplayName("Cassandra 5.0 Vector Operations")
  class VectorOperations {

    @Test
    @Order(1)
    @DisplayName("Create collection with vector support")
    public void createVectorCollection() {
      String collectionName = "vector_collection_" + RandomStringUtils.randomAlphanumeric(8);

      // Create collection with vector configuration
      String createRequest =
              """
          {
            "name": "%s",
            "vectorConfig": {
              "enabled": true,
              "dimension": %d,
              "metric": "cosine"
            }
          }
          """
              .formatted(collectionName, VECTOR_DIMENSION_STANDARD);

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(createRequest)
          .when()
          .post("/v2/namespaces/{namespace}/collections", DEFAULT_NAMESPACE)
          .then()
          .statusCode(201)
          .body("name", equalTo(collectionName));

      LOG.info("Created vector collection: {}", collectionName);
    }

    @Test
    @Order(2)
    @DisplayName("Insert documents with small vectors")
    public void insertDocumentsWithSmallVectors() {
      // Document 1: About technology
      String doc1 =
          """
          {
            "id": "tech_doc_1",
            "title": "Introduction to AI",
            "content": "Artificial Intelligence is transforming technology",
            "vector": [0.1, 0.2, 0.3]
          }
          """;

      // Document 2: About nature
      String doc2 =
          """
          {
            "id": "nature_doc_1",
            "title": "Forest Ecosystem",
            "content": "Forests are complex ecological systems",
            "vector": [0.4, 0.5, 0.6]
          }
          """;

      // Document 3: Similar to doc1 (technology)
      String doc3 =
          """
          {
            "id": "tech_doc_2",
            "title": "Machine Learning Basics",
            "content": "ML is a subset of artificial intelligence",
            "vector": [0.15, 0.25, 0.35]
          }
          """;

      // Insert all documents
      insertDocument("tech_doc_1", doc1);
      insertDocument("nature_doc_1", doc2);
      insertDocument("tech_doc_2", doc3);

      LOG.info("Inserted 3 documents with {}-dimensional vectors", VECTOR_DIMENSION_SMALL);
    }

    @Test
    @Order(3)
    @DisplayName("Vector similarity search")
    public void vectorSimilaritySearch() {
      // Search with a vector similar to technology documents
      String searchRequest =
          """
          {
            "vector": [0.12, 0.22, 0.32],
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

      // Verify we get technology documents back (should be similar)
      List<Map<String, Object>> documents = response.jsonPath().getList("data");
      assertThat(documents).hasSize(2);

      // Check that returned documents are the technology ones
      for (Map<String, Object> doc : documents) {
        assertThat(doc).containsKeys("id", "title", "content", "vector");
        String id = (String) doc.get("id");
        assertThat(id).startsWith("tech_doc_");
        LOG.info("Found similar document: {} - {}", id, doc.get("title"));
      }
    }

    @Test
    @Order(4)
    @DisplayName("Insert document with standard OpenAI embedding size")
    public void insertDocumentWithStandardVector() throws JsonProcessingException {
      // Create a vector of standard size (1536 dimensions)
      double[] standardVector = new double[VECTOR_DIMENSION_STANDARD];
      for (int i = 0; i < VECTOR_DIMENSION_STANDARD; i++) {
        standardVector[i] = Math.sin(i * 0.01) * 0.5; // Generate some pattern
      }

      String doc =
          String.format(
              """
          {
            "id": "openai_doc_1",
            "title": "Document with OpenAI embedding",
            "content": "This document uses standard OpenAI embedding dimensions",
            "embedding": %s,
            "metadata": {
              "source": "openai",
              "model": "text-embedding-ada-002",
              "dimension": %d
            }
          }
          """,
              objectMapper.writeValueAsString(standardVector), VECTOR_DIMENSION_STANDARD);

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(doc)
          .when()
          .put(BASE_PATH + "/{docId}", DEFAULT_NAMESPACE, DEFAULT_COLLECTION, "openai_doc_1")
          .then()
          .statusCode(200);

      LOG.info("Inserted document with {}-dimensional vector", VECTOR_DIMENSION_STANDARD);
    }

    @Test
    @Order(5)
    @DisplayName("Test Cassandra 5.0 specific vector features")
    public void testCassandra5VectorFeatures() {
      // Test 1: Vector indexing with SAI (Storage Attached Index)
      // Cassandra 5.0 introduces SAI for better vector search performance

      // Insert multiple documents to test indexing
      for (int i = 0; i < 10; i++) {
        String doc =
                """
            {
              "id": "indexed_doc_%d",
              "title": "Indexed Document %d",
              "vector": [%f, %f, %f]
            }
            """
                .formatted(i, i, i * 0.1, i * 0.2, i * 0.3);

        insertDocument("indexed_doc_" + i, doc);
      }

      // Search and verify performance with larger dataset
      String searchRequest =
          """
          {
            "vector": [5.0, 0.5, 1.0],
            "limit": 5
          }
          """;

      Response response =
          given()
              .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
              .contentType(ContentType.JSON)
              .queryParam("profile", true) // Enable profiling to see query execution
              .body(searchRequest)
              .when()
              .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
              .then()
              .statusCode(200)
              .body("data", hasSize(5))
              .body("profile", notNullValue())
              .extract()
              .response();

      // Check if SAI is being used (would be in profile information)
      Map<String, Object> profile = response.jsonPath().getMap("profile");
      LOG.info("Query profile: {}", profile);
    }

    @Test
    @Order(6)
    @DisplayName("Test vector dimension validation")
    public void testVectorDimensionValidation() {
      // Test 1: Wrong dimension (too few)
      String wrongDimDoc =
          """
          {
            "id": "wrong_dim_1",
            "vector": [0.1, 0.2]
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(wrongDimDoc)
          .when()
          .put(BASE_PATH + "/{docId}", DEFAULT_NAMESPACE, DEFAULT_COLLECTION, "wrong_dim_1")
          .then()
          .statusCode(400)
          .body("code", equalTo(400))
          .body("description", containsString("dimension"));

      // Test 2: Wrong dimension (too many)
      String tooManyDimDoc =
          """
          {
            "id": "wrong_dim_2",
            "vector": [0.1, 0.2, 0.3, 0.4, 0.5]
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(tooManyDimDoc)
          .when()
          .put(BASE_PATH + "/{docId}", DEFAULT_NAMESPACE, DEFAULT_COLLECTION, "wrong_dim_2")
          .then()
          .statusCode(400);
    }

    @Test
    @Order(7)
    @DisplayName("Test different similarity metrics")
    public void testSimilarityMetrics() {
      // Cassandra 5.0 supports different similarity metrics
      // Create collections with different metrics

      String[] metrics = {"cosine", "euclidean", "dot_product"};

      for (String metric : metrics) {
        String collectionName = "vector_" + metric + "_" + RandomStringUtils.randomAlphanumeric(4);

        String createRequest =
                """
            {
              "name": "%s",
              "vectorConfig": {
                "enabled": true,
                "dimension": 3,
                "metric": "%s"
              }
            }
            """
                .formatted(collectionName, metric);

        // Create collection
        given()
            .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
            .contentType(ContentType.JSON)
            .body(createRequest)
            .when()
            .post("/v2/namespaces/{namespace}/collections", DEFAULT_NAMESPACE)
            .then()
            .statusCode(201);

        // Insert test document
        String doc =
            """
            {
              "id": "test_doc",
              "vector": [1.0, 0.0, 0.0]
            }
            """;

        given()
            .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
            .contentType(ContentType.JSON)
            .body(doc)
            .when()
            .put(
                "/v2/namespaces/{namespace}/collections/{collection}/{docId}",
                DEFAULT_NAMESPACE,
                collectionName,
                "test_doc")
            .then()
            .statusCode(200);

        LOG.info("Created collection with {} similarity metric", metric);
      }
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
  }

  @Nested
  @DisplayName("Cassandra 5.0 Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("Handle vector operations on non-vector collection")
    public void vectorSearchOnNonVectorCollection() {
      // Create a regular collection without vector support
      String regularCollection = "regular_" + RandomStringUtils.randomAlphanumeric(8);

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(
                  """
              {
                "name": "%s"
              }
              """
                  .formatted(regularCollection))
          .when()
          .post("/v2/namespaces/{namespace}/collections", DEFAULT_NAMESPACE)
          .then()
          .statusCode(201);

      // Try vector search on non-vector collection
      String searchRequest =
          """
          {
            "vector": [0.1, 0.2, 0.3],
            "limit": 10
          }
          """;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(searchRequest)
          .when()
          .post(VECTOR_SEARCH_PATH, DEFAULT_NAMESPACE, regularCollection)
          .then()
          .statusCode(400)
          .body("description", containsString("vector"));
    }
  }
}
