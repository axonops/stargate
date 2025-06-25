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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BaseServiceTest {

  private SimpleServiceManager serviceManager;
  private TestService testService;

  @BeforeEach
  public void setup() {
    serviceManager = new SimpleServiceManager();
    testService = new TestService();
    testService.setServiceManager(serviceManager);
  }

  @Test
  public void testServiceStartStop() throws Exception {
    // Test that service can be started
    testService.start();
    assertThat(testService.isStarted()).isTrue();
    assertThat(testService.startCalled).isTrue();

    // Test that service can be stopped
    testService.stop();
    assertThat(testService.stopCalled).isTrue();
  }

  @Test
  public void testServiceRegistration() throws Exception {
    // Start the service which should register TestInterface
    testService.start();

    // Verify service was registered
    TestInterface registered = serviceManager.getService(TestInterface.class);
    assertThat(registered).isNotNull();
    assertThat(registered).isInstanceOf(TestService.class);
  }

  @Test
  public void testServiceRegistrationWithProperties() throws Exception {
    // Create a service that registers with properties
    TestServiceWithProperties serviceWithProps = new TestServiceWithProperties();
    serviceWithProps.setServiceManager(serviceManager);
    serviceWithProps.start();

    // Verify service was registered with properties
    TestInterface registered = serviceManager.getService(TestInterface.class, "id", "test-service");
    assertThat(registered).isNotNull();
    assertThat(registered).isInstanceOf(TestServiceWithProperties.class);

    // Verify null is returned when property doesn't match
    TestInterface notFound = serviceManager.getService(TestInterface.class, "id", "wrong-id");
    assertThat(notFound).isNull();
  }

  @Test
  public void testDependencyResolution() throws Exception {
    // Create dependent service
    DependentService dependentService = new DependentService();
    dependentService.setServiceManager(serviceManager);

    // Start test service first (dependency)
    testService.start();

    // Now start dependent service
    dependentService.start();

    // Verify dependency was resolved
    assertThat(dependentService.getDependency()).isNotNull();
    assertThat(dependentService.getDependency()).isSameAs(testService);
  }

  @Test
  public void testMissingDependencyThrowsException() {
    // Create dependent service
    DependentService dependentService = new DependentService();
    dependentService.setServiceManager(serviceManager);

    // Try to start without dependency available
    Exception exception =
        assertThrows(
            Exception.class,
            () -> {
              dependentService.start();
            });

    assertThat(exception.getMessage()).contains("Failed to resolve dependencies");
  }

  @Test
  public void testServiceLifecycle() throws Exception {
    // Test full lifecycle
    assertThat(testService.isStarted()).isFalse();

    testService.start();
    assertThat(testService.isStarted()).isTrue();

    // Starting again should be a no-op
    testService.start();
    assertThat(testService.startCount).isEqualTo(1);

    testService.stop();
    assertThat(testService.stopCalled).isTrue();
  }

  // Test service implementations
  interface TestInterface {
    String getName();
  }

  static class TestService extends BaseService implements TestInterface {
    boolean startCalled = false;
    boolean stopCalled = false;
    int startCount = 0;

    TestService() {
      super("TestService");
    }

    @Override
    protected void createServices() {
      startCalled = true;
      startCount++;
      register(TestInterface.class, this);
    }

    @Override
    protected void stopServices() {
      stopCalled = true;
    }

    @Override
    public String getName() {
      return "test";
    }
  }

  static class TestServiceWithProperties extends BaseService implements TestInterface {
    TestServiceWithProperties() {
      super("TestServiceWithProperties");
    }

    @Override
    protected void createServices() {
      Map<String, Object> props = new HashMap<>();
      props.put("id", "test-service");
      register(TestInterface.class, this, props);
    }

    @Override
    protected void stopServices() {}

    @Override
    public String getName() {
      return "test-with-props";
    }
  }

  static class DependentService extends BaseService {
    private TestInterface dependency;

    DependentService() {
      super("DependentService");
    }

    @Override
    protected List<ServiceDependency<?>> dependencies() {
      return Collections.singletonList(ServiceDependency.required(TestInterface.class));
    }

    @Override
    protected void createServices() {
      dependency = getService(TestInterface.class);
    }

    @Override
    protected void stopServices() {}

    public TestInterface getDependency() {
      return dependency;
    }
  }
}
