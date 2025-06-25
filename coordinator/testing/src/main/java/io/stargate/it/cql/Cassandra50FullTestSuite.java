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

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test suite that runs all Cassandra 5.0 specific integration tests. This includes CQL features,
 * vector support, and other Cassandra 5.0 specific functionality.
 */
@Suite
@SuiteDisplayName("Cassandra 5.0 Full Integration Test Suite")
@SelectClasses({
  // Core CQL functionality tests
  Cassandra50CqlIntegrationTest.class,

  // Vector functionality tests
  Cassandra50VectorIntegrationTest.class,

  // Include existing tests that should work with Cassandra 5.0
  SimpleStatementTest.class,
  PreparedStatementTest.class,
  BatchStatementTest.class,
  BoundStatementTest.class,
  PaginationTest.class,
  DataTypeTest.class,
  AuthenticationTest.class,
  SchemaChangesTest.class,
  SystemTablesTest.class,
  UseKeyspaceTest.class,
  NowInSecondsTest.class,
  CompositeTypeTest.class,

  // UDF/UDA tests (if enabled)
  UdfTest.class
})
public class Cassandra50FullTestSuite {
  // This class is just a test suite runner
}
