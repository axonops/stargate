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

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceRegistryTest {

  private ServiceRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ServiceRegistry();
  }

  @Test
  void testRegisterAndGetService() {
    // Register a service
    TestService service = new TestServiceImpl();
    ServiceRegistration<TestService> registration = registry.register(TestService.class, service);

    assertNotNull(registration);
    assertEquals(TestService.class, registration.getServiceClass());
    assertEquals(service, registration.getService());

    // Get the service
    TestService retrieved = registry.getService(TestService.class);
    assertEquals(service, retrieved);
  }

  @Test
  void testRegisterWithProperties() {
    TestService service1 = new TestServiceImpl();
    TestService service2 = new TestServiceImpl();

    Map<String, Object> props1 = new HashMap<>();
    props1.put("name", "service1");
    props1.put("version", "1.0");

    Map<String, Object> props2 = new HashMap<>();
    props2.put("name", "service2");
    props2.put("version", "2.0");

    registry.register(TestService.class, service1, props1);
    registry.register(TestService.class, service2, props2);

    // Get by property
    TestService retrieved1 = registry.getService(TestService.class, "name", "service1");
    assertEquals(service1, retrieved1);

    TestService retrieved2 = registry.getService(TestService.class, "name", "service2");
    assertEquals(service2, retrieved2);

    // Get all services
    List<TestService> allServices = registry.getServices(TestService.class);
    assertEquals(2, allServices.size());
    assertTrue(allServices.contains(service1));
    assertTrue(allServices.contains(service2));
  }

  @Test
  void testUnregister() {
    TestService service = new TestServiceImpl();
    ServiceRegistration<TestService> registration = registry.register(TestService.class, service);

    // Verify service is available
    assertNotNull(registry.getService(TestService.class));

    // Unregister via registry
    registry.unregister(registration);

    // Verify service is no longer available
    assertNull(registry.getService(TestService.class));
  }

  @Test
  void testGetServiceWhenNotRegistered() {
    assertNull(registry.getService(TestService.class));
    assertTrue(registry.getServices(TestService.class).isEmpty());
  }

  // Test interfaces and implementations
  interface TestService {
    String getName();
  }

  static class TestServiceImpl implements TestService {
    @Override
    public String getName() {
      return "TestService";
    }
  }
}
