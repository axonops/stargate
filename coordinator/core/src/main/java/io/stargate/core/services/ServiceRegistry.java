package io.stargate.core.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Simple service registry for managing service instances. Services are registered at startup and
 * remain static.
 */
public class ServiceRegistry {
  private final Map<Class<?>, List<ServiceRegistration<?>>> services = new ConcurrentHashMap<>();

  public <T> ServiceRegistration<T> register(Class<T> serviceClass, T implementation) {
    return register(serviceClass, implementation, Collections.emptyMap());
  }

  public <T> ServiceRegistration<T> register(
      Class<T> serviceClass, T implementation, Map<String, Object> properties) {
    ServiceRegistration<T> registration =
        new ServiceRegistration<>(serviceClass, implementation, properties);

    services.compute(
        serviceClass,
        (k, v) -> {
          List<ServiceRegistration<?>> list = v != null ? v : new ArrayList<>();
          list.add(registration);
          return list;
        });

    System.out.println(
        "DEBUG: Registered " + serviceClass.getName() + " with properties: " + properties);
    return registration;
  }

  <T> void unregister(ServiceRegistration<T> registration) {
    Class<T> serviceClass = registration.getServiceClass();
    services.compute(
        serviceClass,
        (k, v) -> {
          if (v != null) {
            v.remove(registration);
            return v.isEmpty() ? null : v;
          }
          return null;
        });
  }

  public <T> T getService(Class<T> serviceClass) {
    List<ServiceRegistration<?>> registrations = services.get(serviceClass);
    if (registrations != null && !registrations.isEmpty()) {
      return serviceClass.cast(registrations.get(0).getService());
    }
    return null;
  }

  public <T> T getService(Class<T> serviceClass, String propertyName, String propertyValue) {
    List<ServiceRegistration<?>> registrations = services.get(serviceClass);
    System.out.println(
        "DEBUG: getService("
            + serviceClass.getName()
            + ", "
            + propertyName
            + ", "
            + propertyValue
            + ")");
    System.out.println(
        "DEBUG: Found "
            + (registrations != null ? registrations.size() : 0)
            + " registrations for "
            + serviceClass.getName());
    if (registrations != null) {
      for (ServiceRegistration<?> reg : registrations) {
        Object value = reg.getProperties().get(propertyName);
        System.out.println(
            "DEBUG: Checking registration with "
                + propertyName
                + "="
                + value
                + " (type: "
                + (value != null ? value.getClass().getName() : "null")
                + ")");
        if (propertyValue.equals(value)) {
          System.out.println("DEBUG: MATCH FOUND!");
          return serviceClass.cast(reg.getService());
        }
      }
    }
    System.out.println("DEBUG: No match found");
    return null;
  }

  public <T> List<T> getServices(Class<T> serviceClass) {
    List<ServiceRegistration<?>> registrations = services.get(serviceClass);
    if (registrations != null) {
      return registrations.stream()
          .map(reg -> serviceClass.cast(reg.getService()))
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }
}
