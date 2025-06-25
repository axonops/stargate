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
package io.stargate.core.services;

import java.util.List;
import java.util.Map;

/** Interface for service management. */
public interface ServiceManager {

  /** Register a service. */
  <T> ServiceRegistration<T> registerService(Class<T> serviceClass, T service);

  /** Register a service with properties. */
  <T> ServiceRegistration<T> registerService(
      Class<T> serviceClass, T service, Map<String, Object> properties);

  /** Get a service by type. */
  <T> T getService(Class<T> serviceClass);

  /** Get a service by type and property filter. */
  <T> T getService(Class<T> serviceClass, String propertyKey, Object propertyValue);

  /** Get all services of a type. */
  <T> List<T> getServices(Class<T> serviceClass);

  /** Wait for a service to be available. */
  <T> T waitForService(Class<T> serviceClass, long timeoutMillis) throws InterruptedException;

  /** Check if a service is available. */
  boolean isServiceAvailable(Class<?> serviceClass);

  /** Start the service manager. */
  void start() throws Exception;

  /** Stop the service manager. */
  void stop() throws Exception;
}
