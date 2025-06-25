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
package io.stargate.testing;

import io.stargate.auth.AuthorizationProcessor;
import io.stargate.core.metrics.api.HttpMetricsTagProvider;
import io.stargate.core.services.BaseService;
import io.stargate.db.metrics.api.ClientInfoMetricsTagProvider;
import io.stargate.grpc.metrics.api.GrpcMetricsTagProvider;
import io.stargate.grpc.metrics.api.UserAgentTagProvider;
import io.stargate.testing.auth.LoggingAuthorizationProcessorImpl;
import io.stargate.testing.metrics.AuthorityGrpcMetricsTagProvider;
import io.stargate.testing.metrics.FixedClientInfoTagProvider;
import io.stargate.testing.metrics.TagMeHttpMetricsTagProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides testing-specific service implementations based on system properties. */
public class TestingServicesModule extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(TestingServicesModule.class);

  public static final String AUTHZ_PROCESSOR_PROPERTY = "stargate.authorization.processor.id";
  public static final String LOGGING_AUTHZ_PROCESSOR_ID = "LoggingAuthzProcessor";

  public static final String HTTP_TAG_PROVIDER_PROPERTY = "stargate.metrics.http_tag_provider.id";
  public static final String TAG_ME_HTTP_TAG_PROVIDER = "TagMeProvider";

  public static final String GRPC_TAG_PROVIDER_PROPERTY = "stargate.metrics.grpc_tag_provider.id";
  public static final String AUTHORITY_GRPC_TAG_PROVIDER = "AuthorityGrpcProvider";
  public static final String USER_AGENT_GRPC_TAG_PROVIDER = "UserAgentGrpcProvider";

  public static final String CLIENT_INFO_TAG_PROVIDER_PROPERTY =
      "stargate.metrics.client_info_tag_provider.id";
  public static final String FIXED_TAG_PROVIDER = "FixedProvider";

  public TestingServicesModule() {
    super("testing-services");
  }

  @Override
  protected void createServices() {
    logger.info("Starting TestingServicesModule");

    // Register logging authorization processor if configured
    if (LOGGING_AUTHZ_PROCESSOR_ID.equals(System.getProperty(AUTHZ_PROCESSOR_PROPERTY))) {
      LoggingAuthorizationProcessorImpl authzProcessor = new LoggingAuthorizationProcessorImpl();
      register(AuthorizationProcessor.class, authzProcessor);
      logger.info("Registered LoggingAuthorizationProcessor");
    }

    // Register HTTP metrics tag provider if configured
    if (TAG_ME_HTTP_TAG_PROVIDER.equals(System.getProperty(HTTP_TAG_PROVIDER_PROPERTY))) {
      TagMeHttpMetricsTagProvider tagProvider = new TagMeHttpMetricsTagProvider();
      register(HttpMetricsTagProvider.class, tagProvider);
      logger.info("Registered TagMeHttpMetricsTagProvider");
    }

    // Register client info tag provider if configured
    if (FIXED_TAG_PROVIDER.equals(System.getProperty(CLIENT_INFO_TAG_PROVIDER_PROPERTY))) {
      FixedClientInfoTagProvider tagProvider = new FixedClientInfoTagProvider();
      register(ClientInfoMetricsTagProvider.class, tagProvider);
      logger.info("Registered FixedClientInfoTagProvider");
    }

    // Register gRPC tag provider if configured
    String grpcTagProvider = System.getProperty(GRPC_TAG_PROVIDER_PROPERTY);
    if (AUTHORITY_GRPC_TAG_PROVIDER.equals(grpcTagProvider)) {
      AuthorityGrpcMetricsTagProvider tagProvider = new AuthorityGrpcMetricsTagProvider();
      register(GrpcMetricsTagProvider.class, tagProvider);
      logger.info("Registered AuthorityGrpcMetricsTagProvider");
    } else if (USER_AGENT_GRPC_TAG_PROVIDER.equals(grpcTagProvider)) {
      GrpcMetricsTagProvider tagProvider = new UserAgentTagProvider() {};
      register(GrpcMetricsTagProvider.class, tagProvider);
      logger.info("Registered UserAgentTagProvider");
    }
  }

  @Override
  protected void stopServices() {
    logger.info("Stopping TestingServicesModule");
    // Services are automatically unregistered by BaseService
  }
}
