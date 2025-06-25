package io.stargate.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.codahale.metrics.health.HealthCheck.Result;
import io.stargate.db.Persistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
class SchemaAgreementCheckerTest {

  private Persistence persistence;
  private SchemaAgreementChecker checker;

  @BeforeEach
  public void setup() {
    persistence = Mockito.mock(Persistence.class);
    checker = new SchemaAgreementChecker(persistence);
  }

  @Test
  public void shouldSucceedWithNoPersistence() {
    checker = new SchemaAgreementChecker(null);
    assertThat(checker.execute()).extracting(Result::isHealthy).isEqualTo(true);
  }

  @Test
  public void shouldSucceedWhenSchemasAgree() {
    when(persistence.isInSchemaAgreement()).thenReturn(true);
    assertThat(checker.execute()).extracting(Result::isHealthy).isEqualTo(true);
  }

  @Test
  public void shouldSucceedWhenSchemaAgreesWithStorage() {
    when(persistence.isInSchemaAgreement()).thenReturn(false);
    when(persistence.isInSchemaAgreementWithStorage()).thenReturn(true);
    assertThat(checker.execute()).extracting(Result::isHealthy).isEqualTo(true);
  }

  @Test
  public void shouldSucceedWhenAgreementIsAchievable() {
    when(persistence.isInSchemaAgreement()).thenReturn(false);
    when(persistence.isSchemaAgreementAchievable()).thenReturn(true);
    assertThat(checker.execute()).extracting(Result::isHealthy).isEqualTo(true);
  }

  @Test
  public void shouldFailWhenAgreementIsNotAchievable() {
    when(persistence.isInSchemaAgreement()).thenReturn(false);
    when(persistence.isSchemaAgreementAchievable()).thenReturn(false);
    assertThat(checker.execute()).extracting(Result::isHealthy).isEqualTo(false);
  }

  @Test
  public void shouldFailOnException() {
    when(persistence.isInSchemaAgreement()).thenThrow(new RuntimeException("test-exception"));
    assertThat(checker.execute()).extracting(Result::isHealthy).isEqualTo(false);
  }
}
