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

package io.stargate.sgv2.docsapi.api.v2.namespaces.collections.documents;

import static io.restassured.RestAssured.given;
import static io.stargate.sgv2.common.IntegrationTestUtils.getAuthToken;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import io.stargate.sgv2.api.common.config.constants.HttpConstants;
import io.stargate.sgv2.common.testresource.StargateTestResource;
import io.stargate.sgv2.docsapi.api.v2.DocsApiIntegrationTest;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for CONTAINS operation with arrays in Document API. Tests that CONTAINS queries
 * with path-aware functionality work correctly.
 */
@QuarkusIntegrationTest
@WithTestResource(value = StargateTestResource.class, restrictToAnnotatedClass = false)
class ContainsFilterIntegrationTest extends DocsApiIntegrationTest {

  public static final String BASE_PATH = "/v2/namespaces/{namespace}/collections/{collection}";
  public static final String DEFAULT_NAMESPACE = RandomStringUtils.randomAlphanumeric(16);
  public static final String DEFAULT_COLLECTION = RandomStringUtils.randomAlphanumeric(16);

  private String[] documentIds;

  @Override
  public Optional<String> createNamespace() {
    return Optional.of(DEFAULT_NAMESPACE);
  }

  @Override
  public Optional<String> createCollection() {
    return Optional.of(DEFAULT_COLLECTION);
  }

  @AfterEach
  void cleanup() {
    if (documentIds != null) {
      deleteDocuments(documentIds);
    }
  }

  @Test
  public void testSimpleArrayContains() {
    // given
    documentIds =
        writeDocuments(
            "{\"name\": \"Product A\", \"tags\": [\"javascript\", \"nodejs\", \"web\"]}",
            "{\"name\": \"Product B\", \"tags\": [\"python\", \"django\", \"web\"]}",
            "{\"name\": \"Product C\", \"tags\": [\"java\", \"spring\", \"backend\"]}");

    // when/then - search for documents with "javascript" in tags array
    String expected =
        "{\"%s\":{\"name\": \"Product A\", \"tags\": [\"javascript\", \"nodejs\", \"web\"]}}"
            .formatted(documentIds[0]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"tags\": {\"$contains\": \"javascript\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testArrayContainsMultipleMatches() {
    // given
    documentIds =
        writeDocuments(
            "{\"name\": \"Doc1\", \"categories\": [\"tech\", \"web\", \"frontend\"]}",
            "{\"name\": \"Doc2\", \"categories\": [\"tech\", \"backend\", \"database\"]}",
            "{\"name\": \"Doc3\", \"categories\": [\"design\", \"ui\", \"web\"]}");

    // when/then - search for documents with "web" in categories
    String expected =
            """
        {
          "%s":{"name": "Doc1", "categories": ["tech", "web", "frontend"]},
          "%s":{"name": "Doc3", "categories": ["design", "ui", "web"]}
        }
        """
            .formatted(documentIds[0], documentIds[2]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"categories\": {\"$contains\": \"web\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testNestedArrayContains() {
    // given
    documentIds =
        writeDocuments(
            "{\"user\": {\"name\": \"Alice\", \"favorites\": [\"coffee\", \"tea\", \"juice\"]}}",
            "{\"user\": {\"name\": \"Bob\", \"favorites\": [\"soda\", \"water\"]}}",
            "{\"user\": {\"name\": \"Charlie\", \"favorites\": [\"coffee\", \"water\"]}}");

    // when/then - search for documents with "coffee" in user.favorites
    String expected =
            """
        {
          "%s":{"user": {"name": "Alice", "favorites": ["coffee", "tea", "juice"]}},
          "%s":{"user": {"name": "Charlie", "favorites": ["coffee", "water"]}}
        }
        """
            .formatted(documentIds[0], documentIds[2]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"user.favorites\": {\"$contains\": \"coffee\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testArrayContainsWithNumbers() {
    // given
    documentIds =
        writeDocuments(
            "{\"student\": \"John\", \"scores\": [85, 92, 88, 95]}",
            "{\"student\": \"Jane\", \"scores\": [78, 85, 90]}",
            "{\"student\": \"Mike\", \"scores\": [92, 94, 96, 98]}");

    // when/then - search for students with score 95
    String expected =
        "{\"%s\":{\"student\": \"John\", \"scores\": [85, 92, 88, 95]}}".formatted(documentIds[0]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"scores\": {\"$contains\": 95}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testArrayContainsWithBooleans() {
    // given
    documentIds =
        writeDocuments(
            "{\"feature\": \"A\", \"flags\": [true, false, true]}",
            "{\"feature\": \"B\", \"flags\": [false, false]}",
            "{\"feature\": \"C\", \"flags\": [true, true, true]}");

    // when/then - search for features with true in flags
    String expected =
            """
        {
          "%s":{"feature": "A", "flags": [true, false, true]},
          "%s":{"feature": "C", "flags": [true, true, true]}
        }
        """
            .formatted(documentIds[0], documentIds[2]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"flags\": {\"$contains\": true}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testArrayContainsPathAwareness() {
    // given - documents with values that could match across different fields
    documentIds =
        writeDocuments(
            "{\"name\": \"javascript\", \"tags\": [\"programming\", \"web\"]}",
            "{\"name\": \"python\", \"tags\": [\"javascript\", \"nodejs\"]}",
            "{\"name\": \"java\", \"description\": \"javascript is different\", \"tags\": [\"backend\", \"enterprise\"]}");

    // when/then - CONTAINS should only match "javascript" within the tags array
    String expected =
        "{\"%s\":{\"name\": \"python\", \"tags\": [\"javascript\", \"nodejs\"]}}"
            .formatted(documentIds[1]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"tags\": {\"$contains\": \"javascript\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testArrayContainsNoMatch() {
    // given
    documentIds =
        writeDocuments(
            "{\"name\": \"Doc1\", \"colors\": [\"red\", \"blue\", \"green\"]}",
            "{\"name\": \"Doc2\", \"colors\": [\"yellow\", \"orange\", \"purple\"]}");

    // when/then - search for non-existent value
    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"colors\": {\"$contains\": \"black\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals("{}"));
  }

  @Test
  public void testArrayContainsWithComplexFilter() {
    // given
    documentIds =
        writeDocuments(
            "{\"product\": \"Laptop\", \"price\": 1200, \"tags\": [\"electronics\", \"computers\", \"portable\"]}",
            "{\"product\": \"Desktop\", \"price\": 800, \"tags\": [\"electronics\", \"computers\", \"office\"]}",
            "{\"product\": \"Tablet\", \"price\": 500, \"tags\": [\"electronics\", \"portable\", \"mobile\"]}");

    // when/then - combine CONTAINS with other filters
    String expected =
            """
        {
          "%s":{"product": "Laptop", "price": 1200, "tags": ["electronics", "computers", "portable"]},
          "%s":{"product": "Tablet", "price": 500, "tags": ["electronics", "portable", "mobile"]}
        }
        """
            .formatted(documentIds[0], documentIds[2]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"tags\": {\"$contains\": \"portable\"}, \"price\": {\"$gt\": 400}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  private String[] writeDocuments(String... json) {
    String[] ids = new String[json.length];

    for (int i = 0; i < json.length; i++) {
      String id = RandomStringUtils.randomAlphanumeric(16);
      ids[i] = id;

      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .contentType(ContentType.JSON)
          .body(json[i])
          .when()
          .put(BASE_PATH + "/{document-id}", DEFAULT_NAMESPACE, DEFAULT_COLLECTION, id)
          .then()
          .statusCode(200);
    }

    return ids;
  }

  private void deleteDocuments(String... ids) {
    for (String id : ids) {
      given()
          .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
          .when()
          .delete(BASE_PATH + "/{document-id}", DEFAULT_NAMESPACE, DEFAULT_COLLECTION, id)
          .then()
          .statusCode(204);
    }
  }
}
