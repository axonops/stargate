package io.stargate.db.cassandra.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.locator.SeedProvider;

public class StargateSeedProvider implements SeedProvider {
  private static final Integer DEFAULT_SEED_PORT = Integer.getInteger("stargate.seed_port", 7000);

  private final List<InetAddressAndPort> seeds;

  public StargateSeedProvider(Map<String, String> args) {
    String seedsStr = args.get("seeds");
    if (seedsStr == null || seedsStr.isEmpty()) {
      throw new ConfigurationException("seeds arg required");
    }

    seeds =
        Arrays.stream(seedsStr.split(","))
            .map(
                s -> {
                  try {
                    return InetAddress.getAllByName(s.trim());
                  } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                  }
                })
            .flatMap(Arrays::stream)
            .map(
                s -> {
                  // In Cassandra 5.0, we need to handle the port explicitly
                  if (DEFAULT_SEED_PORT != null) {
                    return InetAddressAndPort.getByAddressOverrideDefaults(s, DEFAULT_SEED_PORT);
                  } else {
                    // Use the default storage port from DatabaseDescriptor
                    return InetAddressAndPort.getByAddress(s);
                  }
                })
            .collect(Collectors.toList());
  }

  @Override
  public List<InetAddressAndPort> getSeeds() {
    return seeds;
  }
}
