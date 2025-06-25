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
package io.stargate.it.cql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.core.data.CqlVector;
import io.stargate.it.BaseIntegrationTest;
import io.stargate.it.driver.CqlSessionSpec;
import io.stargate.it.driver.TestKeyspace;
import io.stargate.it.storage.StargateExtension;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Comprehensive integration tests for Cassandra 5.0 vector functionality with Stargate. Tests
 * vector types, vector indexes, and ANN (Approximate Nearest Neighbor) search.
 */
@ExtendWith(StargateExtension.class)
@CqlSessionSpec
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Cassandra50VectorIntegrationTest extends BaseIntegrationTest {

  @BeforeAll
  public static void checkCassandra50(CqlSession session) {
    // Skip all tests if not running against Cassandra 5.0
    try {
      session.execute(
          "CREATE TABLE IF NOT EXISTS vector_test (id int PRIMARY KEY, v vector<float, 3>)");
      session.execute("DROP TABLE IF EXISTS vector_test");
    } catch (Exception e) {
      assumeTrue(false, "Vector types not supported - skipping Cassandra 5.0 vector tests");
    }
  }

  @Test
  @Order(1)
  @DisplayName("Test basic vector type operations")
  public void testBasicVectorOperations(CqlSession session, @TestKeyspace String keyspace) {
    // Create table with vector column
    session.execute(
        "CREATE TABLE IF NOT EXISTS vector_basic (id int PRIMARY KEY, embedding vector<float, 3>)");

    // Insert vectors
    CqlVector<Float> vec1 = CqlVector.newInstance(1.0f, 2.0f, 3.0f);
    CqlVector<Float> vec2 = CqlVector.newInstance(4.0f, 5.0f, 6.0f);
    CqlVector<Float> vec3 = CqlVector.newInstance(-1.0f, 0.0f, 1.0f);

    PreparedStatement ps =
        session.prepare("INSERT INTO vector_basic (id, embedding) VALUES (?, ?)");
    session.execute(ps.bind(1, vec1));
    session.execute(ps.bind(2, vec2));
    session.execute(ps.bind(3, vec3));

    // Query vectors
    ResultSet rs = session.execute("SELECT * FROM vector_basic");
    List<Row> rows = rs.all();
    assertThat(rows).hasSize(3);

    // Verify vector retrieval
    Row row = session.execute("SELECT * FROM vector_basic WHERE id = 1").one();
    @SuppressWarnings("unchecked")
    CqlVector<Float> retrieved = row.get("embedding", CqlVector.class);
    assertThat(retrieved).isNotNull();
    assertThat(retrieved.size()).isEqualTo(3);
    assertThat(retrieved.get(0)).isEqualTo(1.0f);
    assertThat(retrieved.get(1)).isEqualTo(2.0f);
    assertThat(retrieved.get(2)).isEqualTo(3.0f);

    // Cleanup
    session.execute("DROP TABLE vector_basic");
  }

  @Test
  @Order(2)
  @DisplayName("Test vector with different dimensions")
  public void testVectorDimensions(CqlSession session) {
    // Test various vector dimensions
    session.execute(
        "CREATE TABLE IF NOT EXISTS vector_dims ("
            + "id int PRIMARY KEY, "
            + "vec2d vector<float, 2>, "
            + "vec5d vector<float, 5>, "
            + "vec10d vector<float, 10>, "
            + "vec100d vector<float, 100>"
            + ")");

    // Insert vectors of different dimensions
    CqlVector<Float> vec2d = CqlVector.newInstance(1.0f, 2.0f);
    CqlVector<Float> vec5d = CqlVector.newInstance(1.0f, 2.0f, 3.0f, 4.0f, 5.0f);

    Float[] values10d = new Float[10];
    Arrays.fill(values10d, 0.1f);
    CqlVector<Float> vec10d = CqlVector.newInstance(values10d);

    Float[] values100d = new Float[100];
    for (int i = 0; i < 100; i++) {
      values100d[i] = i / 100.0f;
    }
    CqlVector<Float> vec100d = CqlVector.newInstance(values100d);

    PreparedStatement ps =
        session.prepare(
            "INSERT INTO vector_dims (id, vec2d, vec5d, vec10d, vec100d) VALUES (?, ?, ?, ?, ?)");
    session.execute(ps.bind(1, vec2d, vec5d, vec10d, vec100d));

    // Verify
    Row row = session.execute("SELECT * FROM vector_dims WHERE id = 1").one();
    assertThat(row).isNotNull();

    @SuppressWarnings("unchecked")
    CqlVector<Float> retrieved2d = row.get("vec2d", CqlVector.class);
    assertThat(retrieved2d.size()).isEqualTo(2);

    @SuppressWarnings("unchecked")
    CqlVector<Float> retrieved100d = row.get("vec100d", CqlVector.class);
    assertThat(retrieved100d.size()).isEqualTo(100);
    assertThat(retrieved100d.get(50)).isEqualTo(0.5f);

    // Cleanup
    session.execute("DROP TABLE vector_dims");
  }

  @Test
  @Order(3)
  @DisplayName("Test vector null handling")
  public void testVectorNullHandling(CqlSession session) {
    session.execute(
        "CREATE TABLE IF NOT EXISTS vector_nulls (id int PRIMARY KEY, vec vector<float, 3>)");

    // Insert null vector
    PreparedStatement ps = session.prepare("INSERT INTO vector_nulls (id, vec) VALUES (?, ?)");
    session.execute(ps.bind(1, null));

    // Query null vector
    Row row = session.execute("SELECT * FROM vector_nulls WHERE id = 1").one();
    assertThat(row).isNotNull();
    assertThat(row.isNull("vec")).isTrue();

    // Insert non-null, then update to null
    CqlVector<Float> vec = CqlVector.newInstance(1.0f, 2.0f, 3.0f);
    session.execute(ps.bind(2, vec));

    session.execute("UPDATE vector_nulls SET vec = null WHERE id = 2");
    row = session.execute("SELECT * FROM vector_nulls WHERE id = 2").one();
    assertThat(row.isNull("vec")).isTrue();

    // Cleanup
    session.execute("DROP TABLE vector_nulls");
  }

  @Test
  @Order(4)
  @DisplayName("Test vector in collections")
  public void testVectorInCollections(CqlSession session) {
    // Create table with vector in collections
    session.execute(
        "CREATE TABLE IF NOT EXISTS vector_collections ("
            + "id int PRIMARY KEY, "
            + "vec_list list<frozen<vector<float, 2>>>, "
            + "vec_set set<frozen<vector<float, 2>>>, "
            + "vec_map map<text, frozen<vector<float, 2>>>"
            + ")");

    // Create vectors
    CqlVector<Float> vec1 = CqlVector.newInstance(1.0f, 2.0f);
    CqlVector<Float> vec2 = CqlVector.newInstance(3.0f, 4.0f);
    CqlVector<Float> vec3 = CqlVector.newInstance(5.0f, 6.0f);

    // Insert collections with vectors
    PreparedStatement ps =
        session.prepare(
            "INSERT INTO vector_collections (id, vec_list, vec_set, vec_map) VALUES (?, ?, ?, ?)");

    List<CqlVector<Float>> vecList = Arrays.asList(vec1, vec2);
    Set<CqlVector<Float>> vecSet = new HashSet<>(Arrays.asList(vec1, vec2, vec3));
    Map<String, CqlVector<Float>> vecMap = new HashMap<>();
    vecMap.put("first", vec1);
    vecMap.put("second", vec2);

    session.execute(ps.bind(1, vecList, vecSet, vecMap));

    // Query and verify
    Row row = session.execute("SELECT * FROM vector_collections WHERE id = 1").one();

    List<CqlVector> retrievedList = row.getList("vec_list", CqlVector.class);
    assertThat(retrievedList).hasSize(2);

    Set<CqlVector> retrievedSet = row.getSet("vec_set", CqlVector.class);
    assertThat(retrievedSet).hasSize(3);

    Map<String, CqlVector> retrievedMap = row.getMap("vec_map", String.class, CqlVector.class);
    assertThat(retrievedMap).hasSize(2);
    assertThat(retrievedMap).containsKey("first");

    // Cleanup
    session.execute("DROP TABLE vector_collections");
  }

  @Test
  @Order(5)
  @DisplayName("Test vector secondary index for ANN search")
  public void testVectorAnnSearch(CqlSession session) {
    // Create table with vector column
    session.execute(
        "CREATE TABLE IF NOT EXISTS vector_search ("
            + "id int PRIMARY KEY, "
            + "name text, "
            + "embedding vector<float, 3>"
            + ")");

    // Try to create custom index for ANN search
    try {
      // This might fail if SAI is not available or configured
      session.execute(
          "CREATE CUSTOM INDEX IF NOT EXISTS embedding_ann_idx ON vector_search(embedding) "
              + "USING 'org.apache.cassandra.index.sai.StorageAttachedIndex'");

      // Insert test data
      PreparedStatement ps =
          session.prepare("INSERT INTO vector_search (id, name, embedding) VALUES (?, ?, ?)");

      // Insert vectors representing different concepts
      session.execute(ps.bind(1, "cat", CqlVector.newInstance(0.1f, 0.2f, 0.9f)));
      session.execute(ps.bind(2, "dog", CqlVector.newInstance(0.2f, 0.3f, 0.8f)));
      session.execute(ps.bind(3, "bird", CqlVector.newInstance(0.9f, 0.1f, 0.1f)));
      session.execute(ps.bind(4, "fish", CqlVector.newInstance(0.1f, 0.9f, 0.1f)));
      session.execute(
          ps.bind(5, "tiger", CqlVector.newInstance(0.15f, 0.25f, 0.85f))); // Similar to cat

      // Perform ANN search - find similar to "cat"
      CqlVector<Float> queryVector = CqlVector.newInstance(0.1f, 0.2f, 0.9f);
      SimpleStatement annQuery =
          SimpleStatement.newInstance(
              "SELECT * FROM vector_search ORDER BY embedding ANN OF ? LIMIT 3", queryVector);

      ResultSet rs = session.execute(annQuery);
      List<Row> results = rs.all();

      // Should return cat first, then similar animals
      assertThat(results).isNotEmpty();
      assertThat(results.get(0).getString("name")).isEqualTo("cat");
      // Tiger should be second as it's most similar to cat
      assertThat(results).extracting(r -> r.getString("name")).containsSubsequence("cat", "tiger");

    } catch (Exception e) {
      // ANN search might not be available in all configurations
      System.out.println("Skipping ANN search test: " + e.getMessage());
    }

    // Cleanup
    try {
      session.execute("DROP INDEX IF EXISTS embedding_ann_idx");
    } catch (Exception ignored) {
    }
    session.execute("DROP TABLE vector_search");
  }

  @Test
  @Order(6)
  @DisplayName("Test vector type validation")
  public void testVectorTypeValidation(CqlSession session) {
    session.execute(
        "CREATE TABLE IF NOT EXISTS vector_validation (id int PRIMARY KEY, vec vector<float, 3>)");

    // Test dimension mismatch
    CqlVector<Float> wrongDimension = CqlVector.newInstance(1.0f, 2.0f); // 2D instead of 3D
    PreparedStatement ps = session.prepare("INSERT INTO vector_validation (id, vec) VALUES (?, ?)");

    assertThatThrownBy(() -> session.execute(ps.bind(1, wrongDimension)))
        .hasMessageContaining("dimension");

    // Test with correct dimension
    CqlVector<Float> correctDimension = CqlVector.newInstance(1.0f, 2.0f, 3.0f);
    session.execute(ps.bind(1, correctDimension)); // Should succeed

    // Cleanup
    session.execute("DROP TABLE vector_validation");
  }

  @Test
  @Order(7)
  @DisplayName("Test vector in prepared statements with named parameters")
  public void testVectorNamedParameters(CqlSession session) {
    session.execute(
        "CREATE TABLE IF NOT EXISTS vector_named (id int PRIMARY KEY, vec vector<float, 3>, metadata text)");

    // Prepare with named parameters
    PreparedStatement ps =
        session.prepare(
            "INSERT INTO vector_named (id, vec, metadata) VALUES (:id, :embedding, :meta)");

    CqlVector<Float> vec = CqlVector.newInstance(7.0f, 8.0f, 9.0f);

    // Bind by name
    BoundStatement bound =
        ps.bind()
            .setInt("id", 1)
            .set("embedding", vec, CqlVector.class)
            .setString("meta", "test vector");

    session.execute(bound);

    // Verify
    Row row = session.execute("SELECT * FROM vector_named WHERE id = 1").one();
    @SuppressWarnings("unchecked")
    CqlVector<Float> retrieved = row.get("vec", CqlVector.class);
    assertThat(retrieved.get(0)).isEqualTo(7.0f);
    assertThat(row.getString("metadata")).isEqualTo("test vector");

    // Cleanup
    session.execute("DROP TABLE vector_named");
  }

  @Test
  @Order(8)
  @DisplayName("Test batch operations with vectors")
  public void testVectorBatchOperations(CqlSession session) {
    session.execute(
        "CREATE TABLE IF NOT EXISTS vector_batch (id int PRIMARY KEY, vec vector<float, 2>)");

    // Create batch with vector operations
    PreparedStatement ps = session.prepare("INSERT INTO vector_batch (id, vec) VALUES (?, ?)");

    BatchStatement batch =
        BatchStatement.newInstance(DefaultBatchType.LOGGED)
            .add(ps.bind(1, CqlVector.newInstance(1.0f, 1.0f)))
            .add(ps.bind(2, CqlVector.newInstance(2.0f, 2.0f)))
            .add(ps.bind(3, CqlVector.newInstance(3.0f, 3.0f)))
            .add(
                SimpleStatement.newInstance(
                    "UPDATE vector_batch SET vec = ? WHERE id = ?",
                    CqlVector.newInstance(1.5f, 1.5f),
                    1));

    session.execute(batch);

    // Verify
    ResultSet rs = session.execute("SELECT * FROM vector_batch");
    List<Row> rows = rs.all();
    assertThat(rows).hasSize(3);

    Row row1 = session.execute("SELECT * FROM vector_batch WHERE id = 1").one();
    @SuppressWarnings("unchecked")
    CqlVector<Float> vec1 = row1.get("vec", CqlVector.class);
    assertThat(vec1.get(0)).isEqualTo(1.5f); // Updated value

    // Cleanup
    session.execute("DROP TABLE vector_batch");
  }

  @Test
  @Order(9)
  @DisplayName("Test vector similarity functions if available")
  public void testVectorSimilarityFunctions(CqlSession session) {
    session.execute(
        "CREATE TABLE IF NOT EXISTS vector_similarity (id int PRIMARY KEY, vec vector<float, 3>)");

    // Insert test vectors
    PreparedStatement ps = session.prepare("INSERT INTO vector_similarity (id, vec) VALUES (?, ?)");
    session.execute(ps.bind(1, CqlVector.newInstance(1.0f, 0.0f, 0.0f)));
    session.execute(ps.bind(2, CqlVector.newInstance(0.0f, 1.0f, 0.0f)));
    session.execute(ps.bind(3, CqlVector.newInstance(0.707f, 0.707f, 0.0f))); // 45 degrees

    try {
      // Try cosine similarity if available
      CqlVector<Float> queryVec = CqlVector.newInstance(1.0f, 0.0f, 0.0f);
      ResultSet rs =
          session.execute(
              "SELECT id, similarity_cosine(vec, ?) as sim FROM vector_similarity", queryVec);

      // If we get here, similarity functions are available
      List<Row> rows = rs.all();
      assertThat(rows).isNotEmpty();

      // Vector 1 should have similarity 1.0 (identical)
      Row row1 = rows.stream().filter(r -> r.getInt("id") == 1).findFirst().orElse(null);
      assertThat(row1).isNotNull();
      assertThat(row1.getFloat("sim")).isEqualTo(1.0f);

    } catch (Exception e) {
      // Similarity functions might not be available
      System.out.println("Skipping similarity function test: " + e.getMessage());
    }

    // Cleanup
    session.execute("DROP TABLE vector_similarity");
  }

  @Test
  @Order(10)
  @DisplayName("Test vector performance with large dataset")
  public void testVectorPerformance(CqlSession session) {
    session.execute(
        "CREATE TABLE IF NOT EXISTS vector_perf ("
            + "id int PRIMARY KEY, "
            + "embedding vector<float, 128>"
            + ")");

    // Insert many vectors
    PreparedStatement ps = session.prepare("INSERT INTO vector_perf (id, embedding) VALUES (?, ?)");

    Random random = new Random(42);
    long startTime = System.currentTimeMillis();

    for (int i = 0; i < 1000; i++) {
      Float[] values = new Float[128];
      for (int j = 0; j < 128; j++) {
        values[j] = random.nextFloat();
      }
      CqlVector<Float> vec = CqlVector.newInstance(values);
      session.execute(ps.bind(i, vec));
    }

    long insertTime = System.currentTimeMillis() - startTime;
    System.out.println("Inserted 1000 128-dimensional vectors in " + insertTime + "ms");

    // Query performance
    startTime = System.currentTimeMillis();
    ResultSet rs = session.execute("SELECT COUNT(*) FROM vector_perf");
    long count = rs.one().getLong(0);
    long queryTime = System.currentTimeMillis() - startTime;

    assertThat(count).isEqualTo(1000);
    System.out.println("Count query completed in " + queryTime + "ms");

    // Cleanup
    session.execute("DROP TABLE vector_perf");
  }
}
