package io.stargate.db.cassandra.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.cassandra.db.virtual.VirtualKeyspaceRegistry;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.TableId;
import org.apache.cassandra.schema.TableMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for handling virtual keyspaces and tables in Cassandra 5.0. This is needed because
 * virtual tables are not part of the regular schema system and need special handling.
 */
public class VirtualSchemaSupport {
  private static final Logger logger = LoggerFactory.getLogger(VirtualSchemaSupport.class);

  /** Get all virtual keyspace metadata including system_virtual_schema */
  public static Collection<KeyspaceMetadata> getVirtualKeyspaces() {
    List<KeyspaceMetadata> virtualKeyspaces = new ArrayList<>();

    try {
      // Get all virtual keyspaces from the registry
      VirtualKeyspaceRegistry registry = VirtualKeyspaceRegistry.instance;

      // Use virtualKeyspacesMetadata() to get all virtual keyspace metadata
      for (KeyspaceMetadata metadata : registry.virtualKeyspacesMetadata()) {
        virtualKeyspaces.add(metadata);
        logger.debug("Found virtual keyspace: {}", metadata.name);
      }
    } catch (Exception e) {
      logger.error("Failed to get virtual keyspaces", e);
    }

    return virtualKeyspaces;
  }

  /** Check if a keyspace is virtual */
  public static boolean isVirtualKeyspace(String keyspaceName) {
    return VirtualKeyspaceRegistry.instance.getKeyspaceMetadataNullable(keyspaceName) != null;
  }

  /** Get virtual table by ID */
  public static TableMetadata getVirtualTableById(TableId tableId) {
    try {
      // Use the direct method to get table metadata by ID
      TableMetadata metadata = VirtualKeyspaceRegistry.instance.getTableMetadataNullable(tableId);
      if (metadata != null) {
        logger.debug("Found virtual table {} for ID {}", metadata.name, tableId);
      }
      return metadata;
    } catch (Exception e) {
      logger.error("Failed to get virtual table by ID: {}", tableId, e);
    }

    return null;
  }

  /** Get all table IDs from virtual keyspaces */
  public static Collection<TableId> getAllVirtualTableIds() {
    List<TableId> tableIds = new ArrayList<>();

    try {
      VirtualKeyspaceRegistry registry = VirtualKeyspaceRegistry.instance;

      // Iterate through all virtual keyspace metadata
      for (KeyspaceMetadata ksMetadata : registry.virtualKeyspacesMetadata()) {
        // Get table IDs from each virtual keyspace
        for (TableMetadata tableMetadata : ksMetadata.tables) {
          tableIds.add(tableMetadata.id);
          logger.debug("Found virtual table {} with ID {}", tableMetadata.name, tableMetadata.id);
        }
      }
    } catch (Exception e) {
      logger.error("Failed to get virtual table IDs", e);
    }

    return tableIds;
  }
}
