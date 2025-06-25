package org.apache.cassandra.transport;

import java.util.UUID;

/**
 * Helper class needed to access tracing id from Message.Response to copy it to converted response.
 */
public class Cassandra50TracingIdAccessor {
  // Needed because C-4.0 does not expose `getTracingId()` as public unlike 3.11
  public static UUID getTracingId(Message.Response response) {
    // In Cassandra 5.0, getTracingId returns TimeUUID which extends UUID
    // We need to convert it to regular UUID
    org.apache.cassandra.utils.TimeUUID tracingId = response.getTracingId();
    return tracingId == null ? null : tracingId.asUUID();
  }
}
