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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Represents a registered service in the service registry. */
public class ServiceRegistration<T> {
  private final Class<T> serviceClass;
  private final T service;
  private final Map<String, Object> properties;
  private final long registrationTime;

  public ServiceRegistration(Class<T> serviceClass, T service, Map<String, Object> properties) {
    this.serviceClass = Objects.requireNonNull(serviceClass, "serviceClass cannot be null");
    this.service = Objects.requireNonNull(service, "service cannot be null");
    this.properties = new HashMap<>(properties != null ? properties : Collections.emptyMap());
    this.registrationTime = System.currentTimeMillis();
  }

  public Class<T> getServiceClass() {
    return serviceClass;
  }

  public T getService() {
    return service;
  }

  public Map<String, Object> getProperties() {
    return Collections.unmodifiableMap(properties);
  }

  public Object getProperty(String key) {
    return properties.get(key);
  }

  public long getRegistrationTime() {
    return registrationTime;
  }

  /** Update properties of this registration. */
  public void setProperties(Map<String, Object> newProperties) {
    properties.clear();
    if (newProperties != null) {
      properties.putAll(newProperties);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ServiceRegistration<?> that = (ServiceRegistration<?>) o;
    return registrationTime == that.registrationTime
        && Objects.equals(serviceClass, that.serviceClass)
        && Objects.equals(service, that.service);
  }

  @Override
  public int hashCode() {
    return Objects.hash(serviceClass, service, registrationTime);
  }

  @Override
  public String toString() {
    return "ServiceRegistration{"
        + "serviceClass="
        + serviceClass.getName()
        + ", service="
        + service.getClass().getName()
        + ", properties="
        + properties
        + ", registrationTime="
        + registrationTime
        + '}';
  }
}
