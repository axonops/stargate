package io.stargate.starter;

import static org.junit.jupiter.api.Assertions.*;

import io.stargate.core.CoreModule;
import io.stargate.core.metrics.impl.MetricsModule;
import io.stargate.core.services.BaseService;
import io.stargate.core.services.ServiceManager;
import io.stargate.core.services.ServiceRegistry;
import io.stargate.core.services.SimpleServiceManager;
import io.stargate.db.cassandra.Cassandra50PersistenceService;
import org.junit.jupiter.api.Test;

public class OsgiRemovalTest {

  @Test
  public void testServiceRegistryWorks() {
    ServiceRegistry registry = new ServiceRegistry();

    // Test service registration
    String testService = "test";
    registry.register(String.class, testService);

    assertEquals(testService, registry.getService(String.class));
  }

  @Test
  public void testBaseServiceCanBeInstantiated() {
    // Test that our converted services can be instantiated
    BaseService metrics = new MetricsModule();
    assertNotNull(metrics);
    assertEquals("MetricsModule", metrics.getServiceName());

    BaseService core = new CoreModule();
    assertNotNull(core);
    assertEquals("core-services", core.getServiceName());

    BaseService persistence = new Cassandra50PersistenceService();
    assertNotNull(persistence);
    assertEquals("Cassandra50Persistence", persistence.getServiceName());
  }

  @Test
  public void testServiceManagerWorks() {
    ServiceManager manager = new SimpleServiceManager();
    assertNotNull(manager);

    // Register a test service
    String testService = "test";
    manager.registerService(String.class, testService);

    assertEquals(testService, manager.getService(String.class));
  }

  @Test
  public void testStarterCanBeInstantiated() {
    Starter starter = new Starter();
    assertNotNull(starter);
  }
}
