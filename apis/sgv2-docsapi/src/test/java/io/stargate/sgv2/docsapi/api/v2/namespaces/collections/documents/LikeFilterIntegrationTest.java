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
 * Integration tests for LIKE operation in Document API. Tests that LIKE queries with pattern
 * matching work correctly with Cassandra 5.0 SAI.
 */
@QuarkusIntegrationTest
@WithTestResource(value = StargateTestResource.class, restrictToAnnotatedClass = false)
class LikeFilterIntegrationTest extends DocsApiIntegrationTest {

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
  public void testLikeWithPercentWildcard() {
    // given
    documentIds =
        writeDocuments(
            "{\"email\": \"john.doe@example.com\", \"name\": \"John Doe\"}",
            "{\"email\": \"jane.smith@example.org\", \"name\": \"Jane Smith\"}",
            "{\"email\": \"bob.wilson@test.com\", \"name\": \"Bob Wilson\"}");

    // when/then - search for emails ending with example.com
    String expected =
        "{\"%s\":{\"email\": \"john.doe@example.com\", \"name\": \"John Doe\"}}"
            .formatted(documentIds[0]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"email\": {\"$like\": \"%@example.com\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testLikeWithUnderscoreWildcard() {
    // given
    documentIds =
        writeDocuments(
            "{\"code\": \"ABC123\", \"product\": \"Product A\"}",
            "{\"code\": \"ABC456\", \"product\": \"Product B\"}",
            "{\"code\": \"XYZ123\", \"product\": \"Product C\"}");

    // when/then - search for codes matching ABC_23 pattern
    String expected =
        "{\"%s\":{\"code\": \"ABC123\", \"product\": \"Product A\"}}".formatted(documentIds[0]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"code\": {\"$like\": \"ABC_23\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testLikeWithPrefixMatch() {
    // given
    documentIds =
        writeDocuments(
            "{\"path\": \"/home/user/documents/file1.txt\"}",
            "{\"path\": \"/home/user/pictures/photo.jpg\"}",
            "{\"path\": \"/var/log/system.log\"}");

    // when/then - search for paths starting with /home/user/
    String expected =
            """
        {
          "%s":{"path": "/home/user/documents/file1.txt"},
          "%s":{"path": "/home/user/pictures/photo.jpg"}
        }
        """
            .formatted(documentIds[0], documentIds[1]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"path\": {\"$like\": \"/home/user/%\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testLikeWithSuffixMatch() {
    // given
    documentIds =
        writeDocuments(
            "{\"filename\": \"document.pdf\", \"size\": 1024}",
            "{\"filename\": \"image.jpg\", \"size\": 2048}",
            "{\"filename\": \"report.pdf\", \"size\": 512}");

    // when/then - search for PDF files
    String expected =
            """
        {
          "%s":{"filename": "document.pdf", "size": 1024},
          "%s":{"filename": "report.pdf", "size": 512}
        }
        """
            .formatted(documentIds[0], documentIds[2]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"filename\": {\"$like\": \"%.pdf\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testLikeWithComplexPattern() {
    // given
    documentIds =
        writeDocuments(
            "{\"url\": \"https://api.example.com/v1/users\"}",
            "{\"url\": \"https://api.example.com/v2/products\"}",
            "{\"url\": \"https://test.example.com/v1/users\"}");

    // when/then - search for API v1 endpoints
    String expected =
        "{\"%s\":{\"url\": \"https://api.example.com/v1/users\"}}".formatted(documentIds[0]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"url\": {\"$like\": \"https://api.%/v1/%\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testLikeWithNestedField() {
    // given
    documentIds =
        writeDocuments(
            "{\"user\": {\"username\": \"admin_user\", \"role\": \"admin\"}}",
            "{\"user\": {\"username\": \"test_user\", \"role\": \"user\"}}",
            "{\"user\": {\"username\": \"admin_guest\", \"role\": \"guest\"}}");

    // when/then - search for admin usernames
    String expected =
            """
        {
          "%s":{"user": {"username": "admin_user", "role": "admin"}},
          "%s":{"user": {"username": "admin_guest", "role": "guest"}}
        }
        """
            .formatted(documentIds[0], documentIds[2]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"user.username\": {\"$like\": \"admin_%\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testLikeWithEscapedCharacters() {
    // given
    documentIds =
        writeDocuments(
            "{\"pattern\": \"value_with_underscore\"}",
            "{\"pattern\": \"value%with%percent\"}",
            "{\"pattern\": \"normal_value\"}");

    // when/then - search for patterns with literal underscore
    String expected =
            """
        {
          "%s":{"pattern": "value_with_underscore"},
          "%s":{"pattern": "normal_value"}
        }
        """
            .formatted(documentIds[0], documentIds[2]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"pattern\": {\"$like\": \"%_value%\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testLikeNoMatch() {
    // given
    documentIds = writeDocuments("{\"name\": \"Alice\"}", "{\"name\": \"Bob\"}");

    // when/then - search for non-existent pattern
    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"name\": {\"$like\": \"Charlie%\"}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals("{}"));
  }

  @Test
  public void testLikeCombinedWithOtherFilters() {
    // given
    documentIds =
        writeDocuments(
            "{\"domain\": \"api.production.com\", \"status\": \"active\", \"requests\": 1000}",
            "{\"domain\": \"api.staging.com\", \"status\": \"active\", \"requests\": 100}",
            "{\"domain\": \"test.production.com\", \"status\": \"inactive\", \"requests\": 0}");

    // when/then - combine LIKE with other conditions
    String expected =
        "{\"%s\":{\"domain\": \"api.production.com\", \"status\": \"active\", \"requests\": 1000}}"
            .formatted(documentIds[0]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam(
            "where",
            "{\"domain\": {\"$like\": \"api.%\"}, \"status\": \"active\", \"requests\": {\"$gt\": 500}}")
        .when()
        .get(BASE_PATH, DEFAULT_NAMESPACE, DEFAULT_COLLECTION)
        .then()
        .statusCode(200)
        .body("documentId", is(nullValue()))
        .body("pageState", is(nullValue()))
        .body("data", jsonEquals(expected));
  }

  @Test
  public void testLikeExactMatch() {
    // given
    documentIds =
        writeDocuments(
            "{\"tag\": \"important\"}",
            "{\"tag\": \"very_important\"}",
            "{\"tag\": \"not_important\"}");

    // when/then - LIKE without wildcards should match exactly
    String expected = "{\"%s\":{\"tag\": \"important\"}}".formatted(documentIds[0]);

    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, getAuthToken())
        .queryParam("where", "{\"tag\": {\"$like\": \"important\"}}")
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
