package io.stargate.core.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for loading Stargate configuration from properties files. Configuration is loaded
 * in the following order (later sources override earlier ones): 1. Default
 * stargate-config.properties from classpath 2. stargate-config.properties from project root 3. File
 * specified by stargate.config.file system property 4. System properties 5. Environment variables
 * (with STARGATE_ prefix)
 */
public class ConfigurationLoader {
  private static final Logger logger = LoggerFactory.getLogger(ConfigurationLoader.class);
  private static final String DEFAULT_CONFIG_FILE = "stargate-config.properties";
  private static final String ENV_PREFIX = "STARGATE_";

  private static Properties configProperties;

  static {
    loadConfiguration();
  }

  private static void loadConfiguration() {
    configProperties = new Properties();

    // 1. Load defaults from classpath
    try (InputStream is =
        ConfigurationLoader.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG_FILE)) {
      if (is != null) {
        configProperties.load(is);
        logger.info("Loaded default configuration from classpath");
      }
    } catch (IOException e) {
      logger.debug("No default configuration found in classpath", e);
    }

    // 2. Load from project root
    File rootConfig = new File(DEFAULT_CONFIG_FILE);
    if (rootConfig.exists()) {
      try (FileInputStream fis = new FileInputStream(rootConfig)) {
        configProperties.load(fis);
        logger.info("Loaded configuration from project root: {}", rootConfig.getAbsolutePath());
      } catch (IOException e) {
        logger.warn("Failed to load configuration from project root", e);
      }
    }

    // 3. Load from custom location
    String customConfigFile = System.getProperty("stargate.config.file");
    if (customConfigFile != null) {
      File customConfig = new File(customConfigFile);
      if (customConfig.exists()) {
        try (FileInputStream fis = new FileInputStream(customConfig)) {
          configProperties.load(fis);
          logger.info(
              "Loaded configuration from custom location: {}", customConfig.getAbsolutePath());
        } catch (IOException e) {
          logger.warn("Failed to load configuration from custom location", e);
        }
      } else {
        logger.warn("Custom configuration file not found: {}", customConfigFile);
      }
    }

    // 4. Override with system properties
    System.getProperties()
        .forEach(
            (key, value) -> {
              String keyStr = key.toString();
              if (keyStr.startsWith("stargate.")
                  || keyStr.startsWith("cassandra.")
                  || keyStr.startsWith("test.")) {
                configProperties.setProperty(keyStr, value.toString());
              }
            });

    // 5. Override with environment variables
    System.getenv()
        .forEach(
            (key, value) -> {
              if (key.startsWith(ENV_PREFIX)) {
                // Convert STARGATE_CLUSTER_NAME to stargate.cluster.name
                String propKey = key.substring(ENV_PREFIX.length()).toLowerCase().replace('_', '.');
                configProperties.setProperty("stargate." + propKey, value);
              }
            });
  }

  /**
   * Get a configuration property value
   *
   * @param key the property key
   * @return the property value, or null if not found
   */
  public static String getProperty(String key) {
    return configProperties.getProperty(key);
  }

  /**
   * Get a configuration property value with a default
   *
   * @param key the property key
   * @param defaultValue the default value to return if the property is not found
   * @return the property value, or the default value if not found
   */
  public static String getProperty(String key, String defaultValue) {
    return configProperties.getProperty(key, defaultValue);
  }

  /**
   * Get a configuration property as an integer
   *
   * @param key the property key
   * @param defaultValue the default value to return if the property is not found or cannot be
   *     parsed
   * @return the property value as an integer, or the default value
   */
  public static int getIntProperty(String key, int defaultValue) {
    String value = configProperties.getProperty(key);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        logger.warn("Invalid integer value for property {}: {}", key, value);
      }
    }
    return defaultValue;
  }

  /**
   * Get a configuration property as a boolean
   *
   * @param key the property key
   * @param defaultValue the default value to return if the property is not found
   * @return the property value as a boolean, or the default value
   */
  public static boolean getBooleanProperty(String key, boolean defaultValue) {
    String value = configProperties.getProperty(key);
    if (value != null) {
      return Boolean.parseBoolean(value);
    }
    return defaultValue;
  }

  /**
   * Get all configuration properties
   *
   * @return a copy of all configuration properties
   */
  public static Properties getAllProperties() {
    return new Properties(configProperties);
  }

  /** Reload configuration from all sources */
  public static void reloadConfiguration() {
    loadConfiguration();
    logger.info("Configuration reloaded");
  }
}
