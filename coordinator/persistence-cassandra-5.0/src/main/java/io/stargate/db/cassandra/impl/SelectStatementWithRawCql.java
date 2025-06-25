package io.stargate.db.cassandra.impl;

/**
 * Extension of SelectStatement that carries the raw CQL query string. This is used by the query
 * interceptor to re-parse statements when needed.
 */
public interface SelectStatementWithRawCql {
  /**
   * Get the raw CQL statement string.
   *
   * @return the original CQL query
   */
  String getRawCQLStatement();
}
