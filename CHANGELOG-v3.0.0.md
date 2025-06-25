# Stargate v3.0.0 Release Notes

## Overview

Stargate v3.0.0 represents a major architectural overhaul focused on simplification, modernization, and new capabilities. This release includes breaking changes that improve performance, reduce complexity, and enable vector search capabilities.

## Breaking Changes

### 1. OSGi Framework Removal
- **Impact**: Complete removal of OSGi framework
- **Rationale**: Simplified architecture, reduced complexity, improved startup time
- **Migration**: Applications must now use standard Java service loading mechanisms

### 2. Cassandra 5.0 Only
- **Impact**: Dropped support for Cassandra 3.11, 4.0, and DSE 6.8
- **Rationale**: Leverage Cassandra 5.0 features including native vector support
- **Migration**: Users must upgrade to Cassandra 5.0 before upgrading Stargate

### 3. Java 17 Requirement
- **Impact**: Minimum Java version raised from 8 to 17
- **Rationale**: Modern Java features, improved performance, Cassandra 5.0 requirement
- **Migration**: Ensure Java 17 is installed in your environment

## New Features

### 1. Vector Search Support
- Native vector similarity search using Cassandra 5.0's vector capabilities
- Support for cosine similarity, dot product, and Euclidean distance
- REST and GraphQL API endpoints for vector operations
- Efficient Approximate Nearest Neighbor (ANN) queries

### 2. Enhanced Filter Operations
- New `$contains` operator for collection searches
- New `$containsKey` operator for map key searches
- New `$like` operator for pattern matching
- Improved query performance with SAI indexes

### 3. Simplified Architecture
- Direct service loading without OSGi bundles
- Faster startup times
- Reduced memory footprint
- Simplified deployment and debugging

## API Changes

### REST API
- New `/v1/keyspaces/{keyspace}/tables/{table}/vector-search` endpoint
- Enhanced filtering capabilities in document search
- Improved error messages and status codes

### GraphQL API
- New `vectorSearch` query type
- Support for vector fields in mutations
- Enhanced type system for vector operations

### Document API
- Vector fields in JSON documents
- Similarity search on document collections
- Combined filtering and vector search

## Performance Improvements

- 30% faster startup time without OSGi overhead
- Reduced memory usage
- Improved query performance with Cassandra 5.0
- Better connection pooling and resource management

## Removed Features

- OSGi bundle support
- Cassandra 3.11, 4.0 persistence layers
- DSE 6.8 persistence layer
- Java 8 compatibility
- Legacy authentication modules

## Migration Guide

### Prerequisites
1. Upgrade to Cassandra 5.0
2. Install Java 17 or later
3. Review API changes

### Steps
1. Backup your data
2. Stop existing Stargate instances
3. Deploy new v3.0.0 packages
4. Update configuration files (remove OSGi-specific settings)
5. Start new instances
6. Verify functionality

### Configuration Changes
- Remove all OSGi-related configuration
- Update JVM options for Java 17
- Review and update security settings

## Known Issues

- Some third-party OSGi bundles are no longer compatible
- Custom persistence implementations need to be rewritten
- Some monitoring tools may need updates for the new architecture

## Future Roadmap

- Enhanced vector search capabilities
- Support for more similarity metrics
- Improved multi-region support
- Performance optimizations

## Support

For questions and support:
- GitHub Issues: https://github.com/stargate/stargate/issues
- Documentation: https://stargate.io/docs

## Contributors

This release represents significant contributions from the Stargate community. Special thanks to all contributors who helped make this architectural transformation possible.