# LIKE Query Implementation Summary

## Overview
This document summarizes the implementation of LIKE query support for the Document API with Cassandra 5.0 SAI (Storage-Attached Index).

## Implementation Components

### 1. Filter Operations
- **LikeFilterOperation** (`filter/operation/impl/LikeFilterOperation.java`)
  - Implements LIKE pattern matching using SQL syntax (% and _ wildcards)
  - Converts LIKE patterns to Java regex for in-memory filtering when needed
  - Case-insensitive matching by default
  - NOTE: NOT LIKE is not supported as it's not available in Cassandra 5.0

### 2. Condition Classes
- **LikeCondition** (`condition/impl/LikeCondition.java`)
  - Core condition implementation for LIKE operations
  - Handles pattern validation and regex conversion
  - Only supports positive LIKE (NOT LIKE not available in Cassandra 5.0)

### 3. Condition Provider
- **LikeConditionProvider** (`condition/provider/impl/LikeConditionProvider.java`)
  - Creates LikeCondition instances from JSON nodes
  - Only accepts string patterns

### 4. Filter Operation Codes
- Added `LIKE("$like")` to FilterOperationCode enum
- NOT_LIKE was not added as it's not supported in Cassandra 5.0

## Usage Example

```json
// Find documents where name starts with "John"
{
  "name": { "$like": "John%" }
}

// Find documents where email ends with "@example.com"
{
  "email": { "$like": "%@example.com" }
}

// Find documents where code contains "TEST"
{
  "code": { "$like": "%TEST%" }
}

// Find documents where id matches pattern "ABC_123"
{
  "id": { "$like": "ABC_123" }
}
```

## Pattern Syntax
- `%` - Matches any sequence of characters (including empty)
- `_` - Matches exactly one character
- Special regex characters in patterns are automatically escaped

## Database-Level LIKE Support
The implementation now supports **database-level LIKE queries** when SAI indexes are present:
- Added LIKE and NOT_LIKE to the Predicate enum
- LikeCondition generates proper CQL with WHERE clause
- Cassandra 5.0 executes LIKE queries efficiently using SAI

### Example Generated CQL:
```sql
-- For filter: { "name": { "$like": "John%" } }
SELECT * FROM collection WHERE text_value LIKE 'John%'

-- For filter: { "status": { "$notLike": "DELETED%" } }
SELECT * FROM collection WHERE text_value NOT LIKE 'DELETED%'
```

## Important Notes
1. **SAI Index Required**: For efficient LIKE queries, ensure SAI indexes are created on string columns
2. **Pattern Syntax**: Uses standard SQL LIKE patterns (% and _)
3. **Case Sensitivity**: Queries are case-insensitive by default (can be configured in SAI index)

## Future Enhancements
1. Add vector similarity search support
2. Update OpenAPI specification with new filter operations
3. Add support for configurable case sensitivity per query
4. Implement CONTAINS operations for collection types

## Testing
- Unit tests for all components:
  - LikeConditionTest
  - LikeFilterOperationTest
  - LikeConditionProviderTest
- Integration tests pending for end-to-end validation

## Notes
- The implementation follows existing patterns in the codebase
- All new classes use Immutables for value objects
- Error handling follows existing ErrorCode patterns
- Case-insensitive matching aligns with typical SQL LIKE behavior