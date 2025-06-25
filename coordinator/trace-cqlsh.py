#!/usr/bin/env python3
"""
Trace what queries cqlsh sends when connecting
"""
import sys
from cassandra.cluster import Cluster
from cassandra.policies import WhiteListRoundRobinPolicy

# Enable query tracing
import logging
logging.basicConfig()
logger = logging.getLogger('cassandra')
logger.setLevel(logging.DEBUG)

try:
    # Connect to Stargate
    cluster = Cluster(
        contact_points=['127.0.0.3'],
        port=19042,
        load_balancing_policy=WhiteListRoundRobinPolicy(['127.0.0.3'])
    )
    
    print("Attempting to connect...")
    session = cluster.connect()
    print("Connected successfully!")
    
    # Try a simple query
    result = session.execute("SELECT release_version FROM system.local")
    for row in result:
        print(f"Release version: {row.release_version}")
    
    cluster.shutdown()
    
except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()