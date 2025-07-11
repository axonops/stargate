package io.stargate.db.cassandra.impl;

import static org.apache.cassandra.concurrent.SharedExecutorPool.SHARED;

import com.datastax.oss.driver.shaded.guava.common.annotations.VisibleForTesting;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableList;
import com.datastax.oss.driver.shaded.guava.common.collect.ImmutableMap;
import com.datastax.oss.driver.shaded.guava.common.collect.Iterables;
import com.datastax.oss.driver.shaded.guava.common.util.concurrent.Uninterruptibles;
import io.stargate.auth.AuthorizationService;
import io.stargate.core.util.TimeSource;
import io.stargate.db.Authenticator;
import io.stargate.db.Batch;
import io.stargate.db.BoundStatement;
import io.stargate.db.ClientInfo;
import io.stargate.db.EventListener;
import io.stargate.db.PagingPosition;
import io.stargate.db.Parameters;
import io.stargate.db.Persistence;
import io.stargate.db.Result;
import io.stargate.db.RowDecorator;
import io.stargate.db.SimpleStatement;
import io.stargate.db.Statement;
import io.stargate.db.cassandra.impl.interceptors.DefaultQueryInterceptor;
import io.stargate.db.cassandra.impl.interceptors.QueryInterceptor;
import io.stargate.db.datastore.common.AbstractCassandraPersistence;
import io.stargate.db.datastore.common.util.SchemaAgreementAchievableCheck;
import io.stargate.db.schema.TableName;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import org.apache.cassandra.auth.AuthenticatedUser;
import org.apache.cassandra.concurrent.LocalAwareExecutorPlus;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.cql3.statements.BatchStatement;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.SystemKeyspace;
import org.apache.cassandra.db.marshal.UserType;
import org.apache.cassandra.exceptions.AuthenticationException;
import org.apache.cassandra.gms.ApplicationState;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.gms.IEndpointStateChangeSubscriber;
import org.apache.cassandra.gms.VersionedValue;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.schema.ColumnMetadata;
import org.apache.cassandra.schema.IndexMetadata;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.Schema;
import org.apache.cassandra.schema.SchemaChangeListener;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.schema.ViewMetadata;
import org.apache.cassandra.service.CassandraDaemon;
import org.apache.cassandra.service.ClientState;
import org.apache.cassandra.service.ClientWarn;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.stargate.exceptions.PersistenceException;
import org.apache.cassandra.stargate.transport.ProtocolVersion;
import org.apache.cassandra.transport.Cassandra50TracingIdAccessor;
import org.apache.cassandra.transport.Message;
import org.apache.cassandra.transport.messages.BatchMessage;
import org.apache.cassandra.transport.messages.ErrorMessage;
import org.apache.cassandra.transport.messages.ExecuteMessage;
import org.apache.cassandra.transport.messages.PrepareMessage;
import org.apache.cassandra.transport.messages.QueryMessage;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.apache.cassandra.transport.messages.StartupMessage;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.JVMStabilityInspector;
import org.apache.cassandra.utils.MD5Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cassandra50Persistence
    extends AbstractCassandraPersistence<
        Config,
        KeyspaceMetadata,
        TableMetadata,
        ColumnMetadata,
        UserType,
        IndexMetadata,
        ViewMetadata> {
  private static final Logger logger = LoggerFactory.getLogger(Cassandra50Persistence.class);

  private static final boolean USE_TRANSITIONAL_AUTH =
      Boolean.getBoolean("stargate.cql_use_transitional_auth");

  /*
   * Initial schema migration can take greater than 2 * MigrationManager.MIGRATION_DELAY_IN_MS if a
   * live token owner doesn't become live within MigrationManager.MIGRATION_DELAY_IN_MS.
   */
  private static final int STARTUP_DELAY_MS =
      Integer.getInteger(
          "stargate.startup_delay_ms",
          3 * 60000); // MigrationManager.MIGRATION_DELAY_IN_MS is private

  // SCHEMA_SYNC_GRACE_PERIOD should be longer than MigrationManager.MIGRATION_DELAY_IN_MS to allow
  // the schema pull tasks to be initiated, plus some time for executing the pull requests plus
  // some time to merge the responses. By default the pull task timeout is equal to
  // DatabaseDescriptor.getRpcTimeout() (10 sec) and there are no retries. We assume that the merge
  // operation should complete within the default MIGRATION_DELAY_IN_MS.
  private static final Duration SCHEMA_SYNC_GRACE_PERIOD =
      Duration.ofMillis(Long.getLong("stargate.schema_sync_grace_period_ms", 2 * 60_000 + 10_000));

  private final SchemaCheck schemaCheck = new SchemaCheck();

  private LocalAwareExecutorPlus executor;

  private CassandraDaemon daemon;
  private Authenticator authenticator;
  private QueryInterceptor interceptor;

  // C* listener that ensures that our Stargate schema remains up-to-date with the internal C* one.
  private SchemaChangeListener schemaChangeListener;
  private AtomicReference<AuthorizationService> authorizationService;

  public Cassandra50Persistence() {
    super("Apache Cassandra");
  }

  private StargateQueryHandler stargateHandler() {
    return (StargateQueryHandler) ClientState.getCQLQueryHandler();
  }

  @Override
  protected SchemaConverter newSchemaConverter() {
    return new SchemaConverter();
  }

  @Override
  protected Iterable<KeyspaceMetadata> currentInternalSchema() {
    return Iterables.transform(org.apache.cassandra.db.Keyspace.all(), Keyspace::getMetadata);
  }

  @Override
  protected void registerInternalSchemaListener(Runnable runOnSchemaChange) {
    schemaChangeListener =
        new SimpleCallbackMigrationListener() {
          @Override
          void onSchemaChange() {
            runOnSchemaChange.run();
          }
        };
    org.apache.cassandra.schema.Schema.instance.registerListener(schemaChangeListener);
  }

  @Override
  protected void unregisterInternalSchemaListener() {
    if (schemaChangeListener != null) {
      org.apache.cassandra.schema.Schema.instance.unregisterListener(schemaChangeListener);
    }
  }

  @Override
  protected void initializePersistence(Config config) {
    // C* picks this property during the static loading of the ClientState class. So we set it
    // early, to make sure that class is not loaded before we've set it.
    System.setProperty(
        "cassandra.custom_query_handler_class", StargateQueryHandler.class.getName());

    // Disable JAMM memory measurement for coordinator-only nodes
    System.setProperty("cassandra.disable_memory_accounting", "true");
    System.setProperty("org.github.jamm.strategies", "NEVER");
    System.setProperty("jamm.skip", "true");

    // Disable native library loading for coordinator-only nodes
    System.setProperty("cassandra.disable_sigar_library", "true");
    System.setProperty("jna.nosys", "true");

    // Set system properties to bypass problematic initializations
    System.setProperty("cassandra.unsafesystem", "true");
    System.setProperty("cassandra.disable_tcactive_openssl", "true");
    System.setProperty("cassandra.disable_sstable_activity_tracking", "true");
    System.setProperty("cassandra.disable_bloom_filter_fp_chance", "true");

    // Try to disable native library access entirely
    System.setProperty("cassandra.disable_native_loading", "true");
    System.setProperty("org.apache.cassandra.disable_tcnative", "true");
    // DO NOT set fd_initial_value_ms to 0 - this causes rapid DOWN/UP cycling
    // System.setProperty("cassandra.fd_initial_value_ms", "0");
    // System.setProperty("cassandra.fd_max_interval_ms", "0");

    daemon = new CassandraDaemon(true);

    DatabaseDescriptor.daemonInitialization(() -> config);
    try {
      daemon.init(null);
    } catch (IOException e) {
      throw new RuntimeException("Unable to start Cassandra persistence layer", e);
    }

    String hostId = System.getProperty("stargate.host_id");
    if (hostId != null && !hostId.isEmpty()) {
      try {
        SystemKeyspace.setLocalHostId(UUID.fromString(hostId));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            String.format("Invalid host ID '%s': not a valid UUID", hostId), e);
      }
    }

    executor =
        SHARED.newExecutor(
            DatabaseDescriptor.getNativeTransportMaxThreads(),
            DatabaseDescriptor::setNativeTransportMaxThreads,
            "transport",
            "Native-Transport-Requests");

    // Use special gossip state "X10" to differentiate stargate nodes
    Gossiper.instance.addLocalApplicationState(
        ApplicationState.X10, StorageService.instance.valueFactory.releaseVersion("stargate"));

    Gossiper.instance.register(schemaCheck);

    daemon.start();

    waitForSchema(STARTUP_DELAY_MS);

    authenticator = new AuthenticatorWrapper(DatabaseDescriptor.getAuthenticator());
    interceptor = new DefaultQueryInterceptor();

    interceptor.initialize();
    stargateHandler().register(interceptor);
    stargateHandler().setAuthorizationService(this.authorizationService);
  }

  @Override
  protected void destroyPersistence() {
    if (daemon != null) {
      daemon.deactivate();
      daemon = null;
    }
  }

  @Override
  public void registerEventListener(EventListener listener) {
    Schema.instance.registerListener(new EventListenerWrapper(listener));
    interceptor.register(listener);
  }

  @Override
  public ByteBuffer unsetValue() {
    return ByteBufferUtil.UNSET_BYTE_BUFFER;
  }

  @Override
  public Authenticator getAuthenticator() {
    return authenticator;
  }

  @Override
  public void setRpcReady(boolean status) {
    StorageService.instance.setRpcReady(status);
  }

  @Override
  public Connection newConnection(ClientInfo clientInfo) {
    return new Cassandra50Connection(clientInfo);
  }

  @Override
  public Connection newConnection() {
    return new Cassandra50Connection();
  }

  private <T extends Result> CompletableFuture<T> runOnExecutor(
      Supplier<T> supplier, boolean captureWarnings) {
    assert executor != null : "This persistence has not been initialized";
    CompletableFuture<T> future = new CompletableFuture<>();
    executor.submit(
        () -> {
          if (captureWarnings) ClientWarn.instance.captureWarnings();
          try {
            @SuppressWarnings("unchecked")
            T resultWithWarnings =
                (T) supplier.get().setWarnings(ClientWarn.instance.getWarnings());
            future.complete(resultWithWarnings);
          } catch (Throwable t) {
            JVMStabilityInspector.inspectThrowable(t);
            PersistenceException pe =
                (t instanceof PersistenceException)
                    ? (PersistenceException) t
                    : Conversion.convertInternalException(t);
            pe.setWarnings(ClientWarn.instance.getWarnings());
            future.completeExceptionally(pe);
          } finally {
            // Note that it's a no-op if we haven't called captureWarnings
            ClientWarn.instance.resetWarnings();
          }
        });

    return future;
  }

  private static boolean shouldCheckSchema(InetAddressAndPort ep) {
    EndpointState epState = Gossiper.instance.getEndpointStateForEndpoint(ep);
    return epState != null && !Gossiper.instance.isDeadState(epState);
  }

  private static boolean isStorageNode(InetAddressAndPort ep) {
    return !Gossiper.instance.isGossipOnlyMember(ep);
  }

  @Override
  public boolean isInSchemaAgreement() {
    // We only include live nodes because this method is mainly used to wait for schema
    // agreement, and waiting for failed nodes is not a great idea.
    // Also note that in theory getSchemaVersion can return null for some nodes, and if it does
    // the code below will likely return false (the null will be an element on its own), but that's
    // probably the right answer in that case. In practice, this shouldn't be a problem though.

    // Important: This must include all nodes including fat clients, otherwise we'll get write
    // errors
    // with INCOMPATIBLE_SCHEMA.

    // Collect schema IDs from all relevant nodes and check that we have at most 1 distinct ID.
    return Gossiper.instance.getLiveMembers().stream()
            .filter(Cassandra50Persistence::shouldCheckSchema)
            .map(Gossiper.instance::getSchemaVersion)
            .distinct()
            .count()
        <= 1;
  }

  @Override
  public boolean isInSchemaAgreementWithStorage() {
    // Collect schema IDs from storage and local node and check that we have at most 1 distinct ID
    InetAddressAndPort localAddress = FBUtilities.getLocalAddressAndPort();
    return Gossiper.instance.getLiveMembers().stream()
            .filter(Cassandra50Persistence::shouldCheckSchema)
            .filter(ep -> isStorageNode(ep) || localAddress.equals(ep))
            .map(Gossiper.instance::getSchemaVersion)
            .distinct()
            .count()
        <= 1;
  }

  /**
   * This method indicates whether storage nodes (i.e. excluding Stargate) agree on the schema
   * version among themselves.
   */
  @VisibleForTesting
  boolean isStorageInSchemaAgreement() {
    // Collect schema IDs from storage nodes and check that we have at most 1 distinct ID.
    return Gossiper.instance.getLiveMembers().stream()
            .filter(Cassandra50Persistence::shouldCheckSchema)
            .filter(Cassandra50Persistence::isStorageNode)
            .map(Gossiper.instance::getSchemaVersion)
            .distinct()
            .count()
        <= 1;
  }

  @Override
  public boolean isSchemaAgreementAchievable() {
    return schemaCheck.check();
  }

  @Override
  public boolean supportsSAI() {
    // Cassandra 5.0 includes SAI (Storage-Attached Indexing)
    return true;
  }

  @Override
  public Map<String, List<String>> cqlSupportedOptions() {
    return ImmutableMap.<String, List<String>>builder()
        .put(StartupMessage.CQL_VERSION, ImmutableList.of(QueryProcessor.CQL_VERSION.toString()))
        .build();
  }

  @Override
  public void executeAuthResponse(Runnable handler) {
    executor.execute(handler);
  }

  /**
   * When "cassandra.join_ring" is "false" {@link StorageService#initServer()} will not wait for
   * schema to propagate to the coordinator only node. This method fixes that limitation by waiting
   * for at least one backend ring member to become available and for their schemas to agree before
   * allowing initialization to continue.
   */
  private void waitForSchema(int delayMillis) {
    boolean isConnectedAndInAgreement = false;
    for (int i = 0; i < delayMillis; i += 1000) {
      if (Gossiper.instance.getLiveTokenOwners().size() > 0 && isInSchemaAgreement()) {
        logger.debug("current schema version: {}", Schema.instance.getVersion());
        isConnectedAndInAgreement = true;
        break;
      }

      Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
    }

    if (!isConnectedAndInAgreement) {
      logger.warn(
          "Unable to connect to live token owner and/or reach schema agreement after {} milliseconds",
          delayMillis);
    }
  }

  public void setAuthorizationService(AtomicReference<AuthorizationService> authorizationService) {
    this.authorizationService = authorizationService;
  }

  private class Cassandra50Connection extends AbstractConnection {
    private volatile ClientState clientState;
    private volatile QueryState queryState;

    Cassandra50Connection() {
      super(null);
      this.clientState = ClientState.forInternalCalls();
      this.queryState = new QueryState(clientState);
    }

    Cassandra50Connection(ClientInfo clientInfo) {
      super(clientInfo);
      this.clientState = ClientState.forExternalCalls(clientInfo.remoteAddress());
      this.queryState = new QueryState(clientState);

      if (!authenticator.requireAuthentication()) {
        clientState.login(AuthenticatedUser.ANONYMOUS_USER);
      }
    }

    @Override
    public Persistence persistence() {
      return Cassandra50Persistence.this;
    }

    protected void loginInternally(io.stargate.db.AuthenticatedUser user) {
      try {
        if (user.isFromExternalAuth() && USE_TRANSITIONAL_AUTH) {
          clientState.login(AuthenticatedUser.ANONYMOUS_USER);
        } else {
          clientState.login(new AuthenticatedUser(user.name()));
        }
      } catch (AuthenticationException e) {
        throw new org.apache.cassandra.stargate.exceptions.AuthenticationException(e);
      }
    }

    // Note: loggedUser() is inherited from AbstractConnection in the base class
    // and returns the AuthenticatedUser set via login()

    @Override
    public Optional<String> usedKeyspace() {
      return Optional.ofNullable(clientState.getRawKeyspace());
    }

    public RowDecorator makeRowDecorator(TableName tableName) {
      return new RowDecoratorImpl(tableName);
    }

    public ByteBuffer makePagingState(PagingPosition position, Parameters parameters) {
      if (position == null || position.resumeFrom() == null) {
        return null;
      }
      // Use the existing toPagingState method from Conversion class
      return Conversion.toPagingState(position, parameters);
    }

    // copyWithCredentials removed - not needed in Cassandra 5.0

    public ByteBuffer makeCustomPayload(
        PagingPosition pagingPosition, Map<String, ByteBuffer> customPayload) {
      // This method is not used in Cassandra 5.0 - paging state is handled by makePagingState
      throw new UnsupportedOperationException(
          "makeCustomPayload is deprecated - use makePagingState instead");
    }

    @Override
    public CompletableFuture<Result> execute(
        Statement statement, Parameters parameters, long queryStartNanoTime) {
      Map<String, ByteBuffer> customPayload = parameters.customPayload().orElse(null);
      return execute(
          statement,
          parameters,
          customPayload != null ? customPayload : ImmutableMap.of(),
          false,
          queryStartNanoTime);
    }

    @Override
    public CompletableFuture<Result.Prepared> prepare(String query, Parameters parameters) {
      String keyspace = parameters.defaultKeyspace().orElse(null);
      Map<String, ByteBuffer> customPayload = parameters.customPayload().orElse(null);
      return prepare(query, keyspace, customPayload != null ? customPayload : ImmutableMap.of());
    }

    @Override
    public CompletableFuture<Result> batch(
        Batch batch, Parameters parameters, long queryStartNanoTime) {
      Map<String, ByteBuffer> customPayload = parameters.customPayload().orElse(null);

      return runOnExecutor(
          () -> {
            QueryOptions options = Conversion.toInternal(Collections.emptyList(), null, parameters);
            BatchStatement.Type internalBatchType = Conversion.toInternal(batch.type());
            List<Object> queryOrIdList = new ArrayList<>(batch.size());
            List<List<ByteBuffer>> allValues = new ArrayList<>(batch.size());

            for (Statement statement : batch.statements()) {
              if (statement instanceof SimpleStatement) {
                queryOrIdList.add(((SimpleStatement) statement).queryString());
              } else {
                queryOrIdList.add(Conversion.toInternal(((BoundStatement) statement).preparedId()));
              }
              allValues.add(statement.values());
            }

            Message.Request request =
                new BatchMessage(internalBatchType, queryOrIdList, allValues, options);
            return handle(
                request,
                customPayload != null ? customPayload : ImmutableMap.of(),
                queryStartNanoTime,
                parameters);
          },
          false);
    }

    protected CompletableFuture<Result> execute(
        Statement statement,
        Parameters parameters,
        @Nonnull Map<String, ByteBuffer> customPayload,
        boolean captureWarnings,
        long queryStartNanoTime) {

      // Check if this is a vector query that needs rewriting
      Statement processedStatement = VectorQueryHandler.processStatement(statement);

      QueryOptions options;

      if (processedStatement instanceof SimpleStatement) {
        SimpleStatement simple = (SimpleStatement) processedStatement;

        // If the statement was rewritten, it has no values
        List<ByteBuffer> values =
            simple.values() != null ? simple.values() : Collections.emptyList();
        List<String> boundNames = simple.boundNames().orElse(null);
        options = Conversion.toInternal(values, boundNames, parameters);

        Message.Request request = new QueryMessage(simple.queryString(), options);
        return runOnExecutor(
            () -> handle(request, customPayload, queryStartNanoTime, parameters), captureWarnings);
      } else if (processedStatement instanceof BoundStatement) {
        BoundStatement bound = (BoundStatement) processedStatement;
        List<String> boundNames = bound.boundNames().orElse(null);
        options = Conversion.toInternal(bound.values(), boundNames, parameters);

        MD5Digest id = Conversion.toInternal(bound.preparedId());
        // In protocol v5, ExecuteMessage takes (statementId, resultMetadataId, options)
        // We use the same id for both as we don't have separate result metadata tracking
        Message.Request request = new ExecuteMessage(id, id, options);
        return runOnExecutor(
            () -> handle(request, customPayload, queryStartNanoTime, parameters), captureWarnings);
      } else {
        throw new UnsupportedOperationException(
            "Unsupported statement type: " + processedStatement.getClass());
      }
    }

    protected CompletableFuture<Result.Prepared> prepare(
        String query, String keyspace, @Nonnull Map<String, ByteBuffer> customPayload) {
      Message.Request request = new PrepareMessage(query, keyspace);
      return runOnExecutor(() -> handlePrepare(request, customPayload), false);
    }

    private Result handle(
        Message.Request request,
        Map<String, ByteBuffer> customPayload,
        long queryStartNanoTime,
        Parameters parameters) {
      request.setCustomPayload(customPayload);
      org.apache.cassandra.transport.Dispatcher.RequestTime requestTime =
          new org.apache.cassandra.transport.Dispatcher.RequestTime(queryStartNanoTime);
      Message.Response response = request.execute(queryState, requestTime);
      if (response instanceof ResultMessage) {
        return Conversion.toResult(
            (ResultMessage) response, Conversion.toInternal(parameters.protocolVersion()));
      } else if (response instanceof ErrorMessage) {
        PersistenceException pe =
            Conversion.convertInternalException((Throwable) ((ErrorMessage) response).error);
        pe.setTracingId(Cassandra50TracingIdAccessor.getTracingId(response));
        throw pe;
      } else {
        // Use convertInternalException to properly wrap the unexpected response
        throw Conversion.convertInternalException(
            new RuntimeException("Unexpected response " + response.type));
      }
    }

    private Result.Prepared handlePrepare(
        Message.Request request, Map<String, ByteBuffer> customPayload) {
      request.setCustomPayload(customPayload);
      // For prepare, we use -1 as the query start time since it's not used
      org.apache.cassandra.transport.Dispatcher.RequestTime requestTime =
          new org.apache.cassandra.transport.Dispatcher.RequestTime(-1);
      Message.Response response = request.execute(queryState, requestTime);
      if (response instanceof ResultMessage.Prepared) {
        // Convert the prepared result - no need for custom payload in the result
        return (Result.Prepared)
            Conversion.toResult(
                (ResultMessage) response, Conversion.toInternal(ProtocolVersion.CURRENT));
      } else if (response instanceof ErrorMessage) {
        PersistenceException pe =
            Conversion.convertInternalException((Throwable) ((ErrorMessage) response).error);
        pe.setTracingId(Cassandra50TracingIdAccessor.getTracingId(response));
        throw pe;
      } else {
        // Use convertInternalException to properly wrap the unexpected response
        throw Conversion.convertInternalException(
            new RuntimeException("Unexpected response " + response.type));
      }
    }

    public void release() {
      // In Cassandra 5.0, logout is handled differently
      // We don't need to explicitly logout the client state
    }
  }

  private class SchemaCheck extends SchemaAgreementAchievableCheck
      implements IEndpointStateChangeSubscriber {

    public SchemaCheck() {
      super(
          Cassandra50Persistence.this::isInSchemaAgreement,
          Cassandra50Persistence.this::isStorageInSchemaAgreement,
          SCHEMA_SYNC_GRACE_PERIOD,
          TimeSource.SYSTEM);
    }

    @Override
    public void onChange(
        InetAddressAndPort endpoint, ApplicationState state, VersionedValue value) {
      // Reset the schema sync grace period timeout on any schema change notifications
      // even if there are no actual changes.
      if (state == ApplicationState.SCHEMA) {
        reset();
      }
    }

    @Override
    public void onJoin(InetAddressAndPort endpoint, EndpointState epState) {}

    @Override
    public void beforeChange(
        InetAddressAndPort endpoint,
        EndpointState currentState,
        ApplicationState newStateKey,
        VersionedValue newValue) {}

    @Override
    public void onAlive(InetAddressAndPort endpoint, EndpointState state) {}

    @Override
    public void onDead(InetAddressAndPort endpoint, EndpointState state) {}

    @Override
    public void onRemove(InetAddressAndPort endpoint) {}

    @Override
    public void onRestart(InetAddressAndPort endpoint, EndpointState state) {}
  }
}
