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
package io.stargate.db;

/** Constants for persistence layer configuration. */
public final class PersistenceConstants {

  /** Identifier for the main persistence service registered by DbModule. */
  public static final String PERSISTENCE_IDENTIFIER = "StargatePersistence";

  /** System property for specifying the rate limiting implementation to use. */
  public static final String RATE_LIMITING_ID_PROPERTY = "stargate.limiter.id";

  private PersistenceConstants() {
    // Prevent instantiation
  }
}
