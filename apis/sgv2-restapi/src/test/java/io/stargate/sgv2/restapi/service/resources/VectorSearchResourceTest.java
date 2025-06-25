package io.stargate.sgv2.restapi.service.resources;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.stargate.sgv2.api.common.config.constants.HttpConstants;
import io.stargate.sgv2.common.testprofiles.NoGlobalResourcesTestProfile;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(NoGlobalResourcesTestProfile.class)
public class VectorSearchResourceTest {

  private static final String TOKEN = "test-token";
  private static final String KEYSPACE = "test_keyspace";
  private static final String TABLE = "test_table";
  private static final String VECTOR_COLUMN = "embedding";
  
  @BeforeEach
  public void setup() {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }
  
  @Test
  public void testVectorSearchWithoutVector() {
    Map<String, Object> requestBody = new HashMap<>();
    
    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, TOKEN)
        .contentType(ContentType.JSON)
        .body(requestBody)
        .when()
        .post("/v2/keyspaces/{keyspace}/tables/{table}/vector-search?vectorColumn={column}",
            KEYSPACE, TABLE, VECTOR_COLUMN)
        .then()
        .statusCode(400)
        .body("code", is(400))
        .body("description", containsString("Vector cannot be null or empty"));
  }
  
  @Test
  public void testVectorSearchWithValidVector() {
    List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f);
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("vector", queryVector);
    
    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, TOKEN)
        .contentType(ContentType.JSON)
        .body(requestBody)
        .queryParam("vectorColumn", VECTOR_COLUMN)
        .queryParam("limit", 5)
        .when()
        .post("/v2/keyspaces/{keyspace}/tables/{table}/vector-search",
            KEYSPACE, TABLE)
        .then()
        .statusCode(anyOf(is(200), is(500))) // 500 if table doesn't exist
        .body("$", hasKey("count"))
        .body("$", hasKey("data"));
  }
  
  @Test
  public void testVectorSearchWithFilters() {
    List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f);
    Map<String, Object> filters = new HashMap<>();
    filters.put("category", "electronics");
    filters.put("price", 100);
    
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("vector", queryVector);
    requestBody.put("filter", filters);
    
    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, TOKEN)
        .contentType(ContentType.JSON)
        .body(requestBody)
        .queryParam("vectorColumn", VECTOR_COLUMN)
        .when()
        .post("/v2/keyspaces/{keyspace}/tables/{table}/vector-search",
            KEYSPACE, TABLE)
        .then()
        .statusCode(anyOf(is(200), is(500))) // 500 if table doesn't exist
        .body("$", hasKey("count"))
        .body("$", hasKey("data"));
  }
  
  @Test
  public void testVectorSearchMissingVectorColumn() {
    List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f);
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("vector", queryVector);
    
    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, TOKEN)
        .contentType(ContentType.JSON)
        .body(requestBody)
        .when()
        .post("/v2/keyspaces/{keyspace}/tables/{table}/vector-search",
            KEYSPACE, TABLE)
        .then()
        .statusCode(400); // vectorColumn is required
  }
  
  @Test
  public void testVectorSearchExceedingLimit() {
    List<Float> queryVector = Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f);
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("vector", queryVector);
    
    given()
        .header(HttpConstants.AUTHENTICATION_TOKEN_HEADER_NAME, TOKEN)
        .contentType(ContentType.JSON)
        .body(requestBody)
        .queryParam("vectorColumn", VECTOR_COLUMN)
        .queryParam("limit", 2000) // Exceeds max limit of 1000
        .when()
        .post("/v2/keyspaces/{keyspace}/tables/{table}/vector-search",
            KEYSPACE, TABLE)
        .then()
        .statusCode(400);
  }
}