package io.stargate.health;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service to provide health check names. This returns a predefined set of health check names. */
public class BundleService {
  private static final Logger logger = LoggerFactory.getLogger(BundleService.class);

  public BundleService() {}

  public Set<String> defaultHealthCheckNames() {
    Set<String> result = new HashSet<>();
    // Add default health check names
    result.add("deadlocks");
    result.add("bundles");
    result.add("datastore");
    result.add("storage");
    result.add("schema-agreement");
    return result;
  }
}
