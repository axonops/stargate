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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.stargate.auth.AuthenticationService;
import io.stargate.auth.AuthorizationService;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthJWTServiceActivator extends BaseService {

  private static final Logger logger = LoggerFactory.getLogger(AuthJWTServiceActivator.class);

  public static final String AUTH_JWT_IDENTIFIER = "AuthJwtService";

  private static final Map<String, Object> props = new HashMap<>();

  static {
    props.put("AuthIdentifier", AUTH_JWT_IDENTIFIER);
  }

  private AuthnJwtService authnJwtService;
  private AuthzJwtService authzJwtService;

  public AuthJWTServiceActivator() {
    super("authnJwtService and authzJwtService");
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    // JWT service has no dependencies
    return Collections.emptyList();
  }

  @Override
  protected void createServices() throws Exception {
    String authId = System.getProperty("stargate.auth_id");

    if (!AUTH_JWT_IDENTIFIER.equals(authId)) {
      logger.info("AuthJwtService not enabled. Current auth_id: {}", authId);
      return;
    }

    logger.info("Starting AuthJwtService");

    String urlProvider = System.getProperty("stargate.auth.jwt_provider_url");
    if (urlProvider == null || urlProvider.isEmpty()) {
      throw new RuntimeException("Property `stargate.auth.jwt_provider_url` must be set");
    }

    ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
    // Pull the public RSA keys from the provided well-known URL to validate the JWT signature.
    JWKSource<SecurityContext> keySource;
    try {
      // by default this will cache the JWK for 15 minutes
      keySource = new RemoteJWKSet<>(new URL(urlProvider));
    } catch (MalformedURLException e) {
      logger.error("Failed to create JwtValidator", e);
      throw new RuntimeException("Failed to create JwtValidator: " + e.getMessage(), e);
    }

    // The expected JWS algorithm of the access tokens
    JWSAlgorithm expectedJWSAlg = JWSAlgorithm.RS256;

    JWSKeySelector<SecurityContext> keySelector =
        new JWSVerificationKeySelector<>(expectedJWSAlg, keySource);
    jwtProcessor.setJWSKeySelector(keySelector);

    authnJwtService = new AuthnJwtService(jwtProcessor);
    register(AuthenticationService.class, authnJwtService, props);

    authzJwtService = new AuthzJwtService();
    register(AuthorizationService.class, authzJwtService, props);

    logger.info("AuthJwtService registered successfully");
  }

  @Override
  protected void stopServices() throws Exception {
    logger.info("Stopping AuthJwtService");
    // Services will be automatically unregistered by BaseService
  }
}
