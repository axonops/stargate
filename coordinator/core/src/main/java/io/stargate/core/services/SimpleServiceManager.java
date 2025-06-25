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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple implementation of ServiceManager that uses the ServiceRegistry. */
public class SimpleServiceManager implements ServiceManager {
  private static final Logger logger = LoggerFactory.getLogger(SimpleServiceManager.class);

  private final ServiceRegistry registry = new ServiceRegistry();
  private volatile boolean started = false;

  @Override
  public <T> ServiceRegistration<T> registerService(Class<T> serviceClass, T service) {
    return registry.register(serviceClass, service);
  }

  @Override
  public <T> ServiceRegistration<T> registerService(
      Class<T> serviceClass, T service, Map<String, Object> properties) {
    return registry.register(serviceClass, service, properties);
  }

  @Override
  public <T> T getService(Class<T> serviceClass) {
    return registry.getService(serviceClass);
  }

  @Override
  public <T> T getService(Class<T> serviceClass, String propertyKey, Object propertyValue) {
    if (propertyValue == null) {
      return null;
    }
    return registry.getService(serviceClass, propertyKey, propertyValue.toString());
  }

  @Override
  public <T> List<T> getServices(Class<T> serviceClass) {
    return registry.getServices(serviceClass);
  }

  @Override
  public <T> T waitForService(Class<T> serviceClass, long timeoutMillis)
      throws InterruptedException {
    // Simple polling implementation
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < timeoutMillis) {
      T service = registry.getService(serviceClass);
      if (service != null) {
        return service;
      }
      Thread.sleep(100);
    }
    return null;
  }

  @Override
  public boolean isServiceAvailable(Class<?> serviceClass) {
    return registry.getService(serviceClass) != null;
  }

  @Override
  public void start() throws Exception {
    if (started) {
      logger.warn("SimpleServiceManager already started");
      return;
    }

    logger.info("Starting SimpleServiceManager");
    started = true;
  }

  @Override
  public void stop() throws Exception {
    if (!started) {
      logger.warn("SimpleServiceManager not started");
      return;
    }

    logger.info("Stopping SimpleServiceManager");
    // Clear registry - not supported in simplified implementation
    started = false;
  }

  public boolean isStarted() {
    return started;
  }
}
