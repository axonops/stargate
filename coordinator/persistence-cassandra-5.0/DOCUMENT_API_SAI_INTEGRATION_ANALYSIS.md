# Document API SAI Integration Analysis

## Current Architecture Overview

### 1. Document Storage Structure

The Document API stores JSON documents in Cassandra tables with the following schema:

```sql
CREATE TABLE namespace.collection (
    key text,                    -- Document ID
    p0 text, p1 text, ..., pN text,  -- Path components (depth configurable)
    leaf text,                   -- Leaf field name
    text_value text,            -- String values
    dbl_value double,           -- Numeric values  
    bool_value boolean/tinyint, -- Boolean values
    PRIMARY KEY (key, p0, p1, ..., pN, leaf)
);
```

**Key Observations:**
- Documents are shredded into individual rows per leaf value
- Path components enable hierarchical navigation
- Different value columns store different data types
- Primary key structure allows efficient document retrieval

### 2. Current Indexing Implementation

From `DocumentDB.java`:

```java
private void createSAIIndexes(String keyspaceName, String tableName) {
    for (String name : DocsApiConstants.VALUE_COLUMN_NAMES) {
        if (name.equals("bool_value") && dataStore.supportsSecondaryIndex()) {
            // SAI doesn't support booleans, so add a non-SAI index here
            createDefaultIndex(keyspaceName, tableName, name);
        } else {
            dataStore.queryBuilder()
                .create()
                .index()
                .ifNotExists()
                .on(keyspaceName, tableName)
                .column(name)
                .custom("StorageAttachedIndex")
                .build()
                .execute()
                .get();
        }
    }
}
```

**Current indexes created:**
- `leaf` column - SAI index
- `text_value` column - SAI index  
- `dbl_value` column - SAI index
- `bool_value` column - Regular secondary index (SAI doesn't support boolean in C* 4.0)

### 3. Query Execution Flow

1. **Filter Expression Parsing**: `ExpressionParser` converts WHERE clauses to `FilterExpression` objects
2. **Query Building**: `FilterExpressionSearchQueryBuilder` constructs CQL queries with appropriate predicates
3. **Document Resolution**: `DocumentsResolver` hierarchy handles different query patterns
4. **Result Assembly**: Rows are reassembled into JSON documents

### 4. Cassandra 5.0 Support

The `Cassandra50Persistence` class confirms SAI support:

```java
@Override
public boolean supportsSAI() {
    // Cassandra 5.0 includes SAI (Storage-Attached Indexing)
    return true;
}
```

## SAI Integration Opportunities

### 1. Enhanced Query Capabilities

With Cassandra 5.0's improved SAI, we can enhance:

- **Composite Queries**: SAI supports AND operations efficiently
- **Range Queries**: Better performance for numeric ranges on `dbl_value`
- **Text Search**: Potential for LIKE/contains operations on `text_value`
- **Boolean Support**: Check if C* 5.0 SAI now supports boolean types

### 2. Path-Based Indexing

Consider creating SAI indexes on path columns (`p0`, `p1`, etc.) to improve:
- Nested field queries
- Array element access
- Wildcard path matching

### 3. Query Optimization Strategies

1. **Index Selection**: Implement cost-based optimizer to choose between indexes
2. **Predicate Pushdown**: Leverage SAI's ability to handle multiple predicates
3. **Pagination**: Optimize cursor-based pagination with SAI

## Recommended Implementation Steps

### Phase 1: Foundation
1. Update index creation logic for C* 5.0 SAI capabilities
2. Add configuration for selective path indexing
3. Implement index usage statistics collection

### Phase 2: Query Enhancement
1. Extend `FilterExpressionSearchQueryBuilder` for composite SAI queries
2. Add support for new SAI operators (if available in C* 5.0)
3. Implement query plan optimization

### Phase 3: Advanced Features
1. Text search capabilities on string fields
2. Geospatial indexing (if supported by C* 5.0 SAI)
3. Array-specific optimizations

## Performance Considerations

1. **Index Overhead**: Monitor write amplification with additional indexes
2. **Memory Usage**: SAI indexes consume memory - plan capacity accordingly
3. **Query Complexity**: Balance between query flexibility and performance

## Testing Strategy

1. **Benchmark Suite**: Compare query performance with/without SAI
2. **Edge Cases**: Test deep nesting, large arrays, mixed data types
3. **Scale Testing**: Validate performance at different data volumes

## Migration Path

For existing Document API users:
1. Provide index upgrade tool (already exists: `upgradeTableIndexes`)
2. Support gradual migration with feature flags
3. Maintain backward compatibility

## Next Steps

1. Review Cassandra 5.0 SAI documentation for new features
2. Design detailed index strategy based on common query patterns
3. Implement proof-of-concept for composite query optimization
4. Create performance benchmarks