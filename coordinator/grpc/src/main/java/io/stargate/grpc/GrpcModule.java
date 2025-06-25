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
package io.stargate.grpc;

import io.stargate.auth.AuthenticationService;
import io.stargate.core.metrics.api.Metrics;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceDependency;
import io.stargate.db.Persistence;
import io.stargate.grpc.impl.GrpcImpl;
import io.stargate.grpc.metrics.api.GrpcMetricsTagProvider;
import io.stargate.grpc.metrics.api.NoopGrpcMetricsTagProvider;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Module that provides gRPC services. */
public class GrpcModule extends BaseService {
  private static final Logger logger = LoggerFactory.getLogger(GrpcModule.class);

  private GrpcImpl grpcServer;

  public GrpcModule() {
    super("GrpcModule", true);
  }

  @Override
  protected List<ServiceDependency<?>> dependencies() {
    String authId = System.getProperty("stargate.auth_id", "AuthTableBasedService");
    return Arrays.asList(
        ServiceDependency.required(Metrics.class),
        ServiceDependency.required(Persistence.class, "Identifier", "StargatePersistence"),
        ServiceDependency.required(AuthenticationService.class, "AuthIdentifier", authId),
        ServiceDependency.optional(GrpcMetricsTagProvider.class));
  }

  @Override
  protected void createServices() throws Exception {
    logger.info("Initializing gRPC services");

    // Get dependencies
    Metrics metrics = getService(Metrics.class);
    Persistence persistence = getService(Persistence.class, "Identifier", "StargatePersistence");
    String authId = System.getProperty("stargate.auth_id", "AuthTableBasedService");
    AuthenticationService authService =
        getService(AuthenticationService.class, "AuthIdentifier", authId);

    // Get optional metrics tag provider
    GrpcMetricsTagProvider grpcMetricsTagProvider =
        getOptionalService(GrpcMetricsTagProvider.class);
    if (grpcMetricsTagProvider == null) {
      grpcMetricsTagProvider = new NoopGrpcMetricsTagProvider();
    }

    // Create and start gRPC server
    grpcServer = new GrpcImpl(persistence, metrics, authService, grpcMetricsTagProvider);
    grpcServer.start();

    int port = Integer.getInteger("stargate.grpc.port", 8090);
    logger.info("gRPC service started on port {}", port);
  }

  @Override
  protected void stopServices() throws Exception {
    logger.info("Stopping gRPC services");
    if (grpcServer != null) {
      grpcServer.stop();
    }
  }
}
