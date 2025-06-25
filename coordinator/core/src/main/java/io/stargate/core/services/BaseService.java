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

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all Stargate services. This class provides lifecycle management and dependency
 * resolution.
 */
public abstract class BaseService {
  private static final Logger logger = LoggerFactory.getLogger(BaseService.class);

  private final String serviceName;
  private final AtomicBoolean available = new AtomicBoolean();
  private final AtomicBoolean started = new AtomicBoolean();
  private final List<ServiceRegistration<?>> registrations = new ArrayList<>();
  private ServiceManager serviceManager;
  private HealthCheck healthCheck;
  private ServiceRegistration<HealthCheck> healthCheckRegistration;

  protected BaseService(String serviceName) {
    this(serviceName, false);
  }

  protected BaseService(String serviceName, boolean registerHealthCheck) {
    this(serviceName, registerHealthCheck, null);
  }

  protected BaseService(String serviceName, ServiceManager serviceManager) {
    this(serviceName, false, serviceManager);
  }

  protected BaseService(
      String serviceName, boolean registerHealthCheck, ServiceManager serviceManager) {
    this.serviceName = serviceName;
    this.serviceManager = serviceManager != null ? serviceManager : new SimpleServiceManager();

    if (registerHealthCheck) {
      this.healthCheck =
          new HealthCheck() {
            @Override
            protected Result check() {
              return available.get()
                  ? Result.healthy("Available")
                  : Result.unhealthy("Not Available");
            }
          };
    }
  }

  /**
   * Get the list of service dependencies. Subclasses should override this to declare their
   * dependencies.
   */
  protected List<ServiceDependency<?>> dependencies() {
    return Collections.emptyList();
  }

  /**
   * Create and register services provided by this module. Subclasses should override this to create
   * their services.
   */
  protected abstract void createServices() throws Exception;

  /** Stop and cleanup services. Subclasses should override this to cleanup their services. */
  protected void stopServices() throws Exception {
    // Default implementation does nothing
  }

  /** Set the service manager for this service. Must be called before start(). */
  public void setServiceManager(ServiceManager serviceManager) {
    if (started.get()) {
      throw new IllegalStateException("Cannot set service manager after service has started");
    }
    this.serviceManager = serviceManager;
  }

  /** Start this service module. */
  public final void start() throws Exception {
    if (started.getAndSet(true)) {
      logger.warn("{} already started", serviceName);
      return;
    }

    logger.info("Starting {}", serviceName);

    try {
      // Wait for dependencies
      if (!waitForDependencies()) {
        throw new Exception("Failed to resolve dependencies for " + serviceName);
      }

      // Register health check if configured
      if (healthCheck != null) {
        HealthCheckRegistry healthRegistry = serviceManager.getService(HealthCheckRegistry.class);
        if (healthRegistry != null) {
          healthRegistry.register(serviceName.toLowerCase(), healthCheck);
          Map<String, Object> props = Collections.singletonMap("name", serviceName);
          healthCheckRegistration =
              serviceManager.registerService(HealthCheck.class, healthCheck, props);
        }
      }

      // Create and register services
      createServices();

      available.set(true);
      logger.info("{} started successfully", serviceName);

    } catch (Exception e) {
      logger.error("Failed to start " + serviceName, e);
      started.set(false);
      throw e;
    }
  }

  /** Stop this service module. */
  public final void stop() throws Exception {
    if (!started.getAndSet(false)) {
      logger.warn("{} not started", serviceName);
      return;
    }

    logger.info("Stopping {}", serviceName);
    available.set(false);

    try {
      // Stop services
      stopServices();

      // Unregister all services
      for (ServiceRegistration<?> registration : registrations) {
        // Unregistration is handled by the service manager
      }
      registrations.clear();

      // Unregister health check
      if (healthCheckRegistration != null) {
        // Health check unregistration is handled by the service manager
        healthCheckRegistration = null;

        HealthCheckRegistry healthRegistry = serviceManager.getService(HealthCheckRegistry.class);
        if (healthRegistry != null) {
          healthRegistry.unregister(serviceName.toLowerCase());
        }
      }

      logger.info("{} stopped successfully", serviceName);

    } catch (Exception e) {
      logger.error("Error stopping " + serviceName, e);
      throw e;
    }
  }

  /** Register a service and track the registration. */
  protected <T> ServiceRegistration<T> register(Class<T> serviceClass, T service) {
    ServiceRegistration<T> registration = serviceManager.registerService(serviceClass, service);
    registrations.add(registration);
    return registration;
  }

  /** Register a service with properties and track the registration. */
  protected <T> ServiceRegistration<T> register(
      Class<T> serviceClass, T service, Map<String, Object> properties) {
    ServiceRegistration<T> registration =
        serviceManager.registerService(serviceClass, service, properties);
    registrations.add(registration);
    return registration;
  }

  /** Get a required service dependency. */
  protected <T> T getService(Class<T> serviceClass) {
    T service = serviceManager.getService(serviceClass);
    if (service == null) {
      throw new IllegalStateException("Required service not available: " + serviceClass.getName());
    }
    return service;
  }

  /** Get an optional service dependency. */
  protected <T> T getOptionalService(Class<T> serviceClass) {
    return serviceManager.getService(serviceClass);
  }

  /** Get a service with specific properties. */
  protected <T> T getService(Class<T> serviceClass, String propertyKey, Object propertyValue) {
    T service = serviceManager.getService(serviceClass, propertyKey, propertyValue);
    if (service == null) {
      throw new IllegalStateException(
          "Required service not available: "
              + serviceClass.getName()
              + " with "
              + propertyKey
              + "="
              + propertyValue);
    }
    return service;
  }

  /** Wait for all declared dependencies to be available. */
  private boolean waitForDependencies() {
    List<ServiceDependency<?>> deps = dependencies();
    if (deps.isEmpty()) {
      return true;
    }

    logger.info("{} waiting for {} dependencies", serviceName, deps.size());

    // Log all dependencies at start
    for (ServiceDependency<?> dep : deps) {
      logger.info("  - {}: {}", serviceName, dep);
    }

    long timeout = 30000; // 30 seconds timeout
    long startTime = System.currentTimeMillis();
    boolean firstLog = true;

    while (System.currentTimeMillis() - startTime < timeout) {
      boolean allAvailable = true;
      ServiceDependency<?> missingDep = null;

      for (ServiceDependency<?> dep : deps) {
        if (!dep.isAvailable(serviceManager)) {
          allAvailable = false;
          missingDep = dep;
          break;
        }
      }

      if (allAvailable) {
        logger.info("{} all dependencies resolved", serviceName);
        return true;
      }

      // Log missing dependency every 5 seconds
      if (firstLog || (System.currentTimeMillis() - startTime) % 5000 < 100) {
        logger.warn("{} still waiting for dependency: {}", serviceName, missingDep);
        firstLog = false;
      }

      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }

    logger.error("{} timed out waiting for dependencies after {}ms", serviceName, timeout);
    for (ServiceDependency<?> dep : deps) {
      boolean available = dep.isAvailable(serviceManager);
      logger.error("  - {} (available: {})", dep, available);
    }
    return false;
  }

  public boolean isAvailable() {
    return available.get();
  }

  public boolean isStarted() {
    return started.get();
  }

  public String getServiceName() {
    return serviceName;
  }
}
