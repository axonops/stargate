package io.stargate.db.cassandra.impl.interceptors;

import io.stargate.db.EventListener;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.cassandra.cql3.CQLStatement;
import org.apache.cassandra.cql3.QueryOptions;
import org.apache.cassandra.service.QueryState;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default interceptor implementation for Cassandra 5.0.
 *
 * <p>In Cassandra 5.0, virtual keyspace handling has changed significantly. For now, we provide a
 * minimal implementation that doesn't intercept queries. The virtual keyspace support can be added
 * later when the exact requirements are clarified.
 */
public class DefaultQueryInterceptor implements QueryInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(DefaultQueryInterceptor.class);

  private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

  @Override
  public void initialize() {
    logger.info("DefaultQueryInterceptor initialized for Cassandra 5.0");
    // Note: Virtual keyspace registration would go here when implemented
  }

  @Override
  public ResultMessage interceptQuery(
      CQLStatement statement,
      QueryState state,
      QueryOptions options,
      Map<String, ByteBuffer> customPayload,
      long queryStartNanoTime) {
    // For now, we don't intercept any queries in Cassandra 5.0
    // This can be enhanced later to handle system.peers filtering if needed
    return null;
  }

  @Override
  public void register(EventListener listener) {
    listeners.add(listener);
  }

  /**
   * Notify listeners about node join. This is a simplified version that converts InetAddressAndPort
   * to InetAddress.
   */
  protected void notifyJoin(InetAddress address, int port) {
    for (EventListener listener : listeners) {
      try {
        listener.onJoinCluster(address, port);
      } catch (Exception e) {
        logger.error("Failed to notify listener about join: {}", address, e);
      }
    }
  }

  /**
   * Notify listeners about node leave. This is a simplified version that converts
   * InetAddressAndPort to InetAddress.
   */
  protected void notifyLeave(InetAddress address, int port) {
    for (EventListener listener : listeners) {
      try {
        listener.onLeaveCluster(address, port);
      } catch (Exception e) {
        logger.error("Failed to notify listener about leave: {}", address, e);
      }
    }
  }
}
