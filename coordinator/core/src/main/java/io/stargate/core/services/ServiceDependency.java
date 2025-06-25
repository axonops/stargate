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

import java.util.Objects;

/** Represents a service dependency that must be satisfied before a service can start. */
public class ServiceDependency<T> {
  private final Class<T> serviceClass;
  private final String propertyKey;
  private final Object propertyValue;
  private final boolean required;

  private ServiceDependency(
      Class<T> serviceClass, String propertyKey, Object propertyValue, boolean required) {
    this.serviceClass = Objects.requireNonNull(serviceClass, "serviceClass cannot be null");
    this.propertyKey = propertyKey;
    this.propertyValue = propertyValue;
    this.required = required;
  }

  /** Create a required dependency on a service. */
  public static <T> ServiceDependency<T> required(Class<T> serviceClass) {
    return new ServiceDependency<>(serviceClass, null, null, true);
  }

  /** Create a required dependency on a service with specific properties. */
  public static <T> ServiceDependency<T> required(
      Class<T> serviceClass, String propertyKey, Object propertyValue) {
    return new ServiceDependency<>(serviceClass, propertyKey, propertyValue, true);
  }

  /** Create an optional dependency on a service. */
  public static <T> ServiceDependency<T> optional(Class<T> serviceClass) {
    return new ServiceDependency<>(serviceClass, null, null, false);
  }

  /** Create an optional dependency on a service with specific properties. */
  public static <T> ServiceDependency<T> optional(
      Class<T> serviceClass, String propertyKey, Object propertyValue) {
    return new ServiceDependency<>(serviceClass, propertyKey, propertyValue, false);
  }

  /** Check if this dependency is satisfied. */
  public boolean isAvailable(ServiceManager serviceManager) {
    T service;
    if (propertyKey != null) {
      service = serviceManager.getService(serviceClass, propertyKey, propertyValue);
    } else {
      service = serviceManager.getService(serviceClass);
    }

    return service != null || !required;
  }

  /** Get the service if available. */
  public T getService(ServiceManager serviceManager) {
    if (propertyKey != null) {
      return serviceManager.getService(serviceClass, propertyKey, propertyValue);
    } else {
      return serviceManager.getService(serviceClass);
    }
  }

  public Class<T> getServiceClass() {
    return serviceClass;
  }

  public boolean isRequired() {
    return required;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(serviceClass.getName());
    if (propertyKey != null) {
      sb.append(" with ").append(propertyKey).append("=").append(propertyValue);
    }
    if (!required) {
      sb.append(" (optional)");
    }
    return sb.toString();
  }
}
