package io.stargate.db.cassandra.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class Cassandra50PersistenceTest {

  @Test
  public void testPersistenceName() {
    Cassandra50Persistence persistence = new Cassandra50Persistence();
    assertThat(persistence.name()).isEqualTo("Apache Cassandra");
  }

  @Test
  public void testSAISupport() {
    Cassandra50Persistence persistence = new Cassandra50Persistence();
    // Cassandra 5.0 includes SAI support
    assertTrue(persistence.supportsSAI());
  }

  @Test
  public void testSchemaConverterCreation() {
    Cassandra50Persistence persistence = new Cassandra50Persistence();
    // This test verifies that the schema converter can be created
    // The actual converter is created internally, so we just verify the persistence object is
    // created
    assertThat(persistence).isNotNull();
  }

  @Test
  public void testUnsetValue() {
    Cassandra50Persistence persistence = new Cassandra50Persistence();
    assertThat(persistence.unsetValue()).isNotNull();
  }
}
