# Cassandra 5.0 Persistence

This module implements the Stargate persistence layer for Apache Cassandra 5.0.

## Overview

This module provides:
- Connection handling for Cassandra 5.0
- Schema management and conversion
- Query execution and result handling
- System keyspace management
- Authorization/authentication integration

## Building

This module is built as part of the main Stargate build:

```bash
mvn clean install
```

## Configuration

The module uses the same configuration properties as other Cassandra persistence modules:
- `stargate.cluster_name`: Cluster name
- `stargate.listen_address`: Node listen address
- `stargate.broadcast_address`: Node broadcast address
- `stargate.cql_port`: CQL port (default: 9042)
- `stargate.seed_port`: Seed port (default: 7000)
- `stargate.seed_list`: Comma-separated list of seed nodes
- `stargate.enable_auth`: Enable authentication (default: false)

## Dependencies

This module depends on:
- Apache Cassandra 5.0.x
- Stargate DB Common
- Stargate Persistence API

## Usage

The module is automatically loaded when running Stargate with `--cluster-version 5.0`.