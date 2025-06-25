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
package io.stargate.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import io.stargate.auth.AuthenticationService;
import io.stargate.auth.AuthorizationService;
import io.stargate.core.services.ServiceRegistration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AuthJWTServiceActivatorTest {

  // Test helper class that tracks service registrations
  private static class TestAuthJWTServiceActivator extends AuthJWTServiceActivator {
    private final List<ServiceRegistrationRecord> registrations = new ArrayList<>();

    private static class ServiceRegistrationRecord {
      final Class<?> serviceClass;
      final Object service;
      final Map<String, Object> properties;

      ServiceRegistrationRecord(
          Class<?> serviceClass, Object service, Map<String, Object> properties) {
        this.serviceClass = serviceClass;
        this.service = service;
        this.properties = properties;
      }
    }

    @Override
    protected <T> ServiceRegistration<T> register(
        Class<T> serviceClass, T service, Map<String, Object> properties) {
      registrations.add(new ServiceRegistrationRecord(serviceClass, service, properties));
      return mock(ServiceRegistration.class);
    }

    public List<ServiceRegistrationRecord> getRegistrations() {
      return registrations;
    }

    public boolean wasServiceRegistered(Class<?> serviceClass) {
      return registrations.stream().anyMatch(r -> r.serviceClass.equals(serviceClass));
    }

    public ServiceRegistrationRecord getRegistration(Class<?> serviceClass) {
      return registrations.stream()
          .filter(r -> r.serviceClass.equals(serviceClass))
          .findFirst()
          .orElse(null);
    }
  }

  private String originalAuthId;
  private String originalJwtProviderUrl;

  @BeforeEach
  public void setUp() {
    // Save original system properties
    originalAuthId = System.getProperty("stargate.auth_id");
    originalJwtProviderUrl = System.getProperty("stargate.auth.jwt_provider_url");
  }

  @AfterEach
  public void tearDown() {
    // Restore original system properties
    if (originalAuthId != null) {
      System.setProperty("stargate.auth_id", originalAuthId);
    } else {
      System.clearProperty("stargate.auth_id");
    }

    if (originalJwtProviderUrl != null) {
      System.setProperty("stargate.auth.jwt_provider_url", originalJwtProviderUrl);
    } else {
      System.clearProperty("stargate.auth.jwt_provider_url");
    }
  }

  @Test
  public void shouldRegisterIfSelectedAuthProvider() throws Exception {
    System.setProperty("stargate.auth.jwt_provider_url", "http://example.com");
    System.setProperty("stargate.auth_id", AuthJWTServiceActivator.AUTH_JWT_IDENTIFIER);

    TestAuthJWTServiceActivator activator = new TestAuthJWTServiceActivator();
    activator.start();

    // Verify that services were registered with correct properties
    Map<String, Object> expectedProps = new HashMap<>();
    expectedProps.put("AuthIdentifier", AuthJWTServiceActivator.AUTH_JWT_IDENTIFIER);

    assertThat(activator.wasServiceRegistered(AuthenticationService.class)).isTrue();
    assertThat(activator.wasServiceRegistered(AuthorizationService.class)).isTrue();

    TestAuthJWTServiceActivator.ServiceRegistrationRecord authNReg =
        activator.getRegistration(AuthenticationService.class);
    assertThat(authNReg).isNotNull();
    assertThat(authNReg.service).isInstanceOf(AuthnJwtService.class);
    assertThat(authNReg.properties).isEqualTo(expectedProps);

    TestAuthJWTServiceActivator.ServiceRegistrationRecord authZReg =
        activator.getRegistration(AuthorizationService.class);
    assertThat(authZReg).isNotNull();
    assertThat(authZReg.service).isInstanceOf(AuthzJwtService.class);
    assertThat(authZReg.properties).isEqualTo(expectedProps);
  }

  @Test
  public void shouldNotRegisterIfNotSelectedAuthProvider() throws Exception {
    System.setProperty("stargate.auth.jwt_provider_url", "http://example.com");
    System.setProperty("stargate.auth_id", "foo");

    TestAuthJWTServiceActivator activator = new TestAuthJWTServiceActivator();
    activator.start();

    // Verify that services were NOT registered
    assertThat(activator.wasServiceRegistered(AuthenticationService.class)).isFalse();
    assertThat(activator.wasServiceRegistered(AuthorizationService.class)).isFalse();
    assertThat(activator.getRegistrations()).isEmpty();
  }

  @Test
  public void shouldNotRegisterIfMissingURL() {
    System.clearProperty("stargate.auth.jwt_provider_url");
    System.setProperty("stargate.auth_id", AuthJWTServiceActivator.AUTH_JWT_IDENTIFIER);

    TestAuthJWTServiceActivator activator = new TestAuthJWTServiceActivator();

    RuntimeException ex = assertThrows(RuntimeException.class, () -> activator.start());
    assertThat(ex).hasMessage("Property `stargate.auth.jwt_provider_url` must be set");

    // Verify that services were NOT registered
    assertThat(activator.wasServiceRegistered(AuthenticationService.class)).isFalse();
    assertThat(activator.wasServiceRegistered(AuthorizationService.class)).isFalse();
    assertThat(activator.getRegistrations()).isEmpty();
  }

  @Test
  public void shouldNotRegisterIfEmptyURL() {
    System.setProperty("stargate.auth.jwt_provider_url", "");
    System.setProperty("stargate.auth_id", AuthJWTServiceActivator.AUTH_JWT_IDENTIFIER);

    TestAuthJWTServiceActivator activator = new TestAuthJWTServiceActivator();

    RuntimeException ex = assertThrows(RuntimeException.class, () -> activator.start());
    assertThat(ex).hasMessage("Property `stargate.auth.jwt_provider_url` must be set");

    // Verify that services were NOT registered
    assertThat(activator.wasServiceRegistered(AuthenticationService.class)).isFalse();
    assertThat(activator.wasServiceRegistered(AuthorizationService.class)).isFalse();
    assertThat(activator.getRegistrations()).isEmpty();
  }

  @Test
  public void shouldNotRegisterIfInvalidURL() {
    System.setProperty("stargate.auth.jwt_provider_url", "foo");
    System.setProperty("stargate.auth_id", AuthJWTServiceActivator.AUTH_JWT_IDENTIFIER);

    TestAuthJWTServiceActivator activator = new TestAuthJWTServiceActivator();

    RuntimeException ex = assertThrows(RuntimeException.class, () -> activator.start());
    assertThat(ex).hasMessage("Failed to create JwtValidator: no protocol: foo");

    // Verify that services were NOT registered
    assertThat(activator.wasServiceRegistered(AuthenticationService.class)).isFalse();
    assertThat(activator.wasServiceRegistered(AuthorizationService.class)).isFalse();
    assertThat(activator.getRegistrations()).isEmpty();
  }
}
