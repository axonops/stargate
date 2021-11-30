package io.stargate.grpc.impl;

import io.grpc.netty.shaded.io.grpc.netty.CustomChannelFactory;
import io.stargate.db.EventListenerWithChannelFilter;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class EventNotifier implements EventListenerWithChannelFilter {

  private final CustomChannelFactory customChannelFactory;

  public EventNotifier(CustomChannelFactory customChannelFactory) {
    this.customChannelFactory = customChannelFactory;
  }

  @Override
  public void onClose(Predicate<Map<String, String>> headerFilter) {
    if (headerFilter != null) {
      customChannelFactory.closeFilter(headerFilter);
    }
  }

  @Override
  public void onCreateKeyspace(String keyspace, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onCreateTable(
      String keyspace, String table, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onCreateType(
      String keyspace, String type, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onCreateFunction(
      String keyspace,
      String function,
      List<String> argumentTypes,
      Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onCreateAggregate(
      String keyspace,
      String aggregate,
      List<String> argumentTypes,
      Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onAlterKeyspace(String keyspace, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onAlterTable(
      String keyspace, String table, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onAlterType(
      String keyspace, String type, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onAlterFunction(
      String keyspace,
      String function,
      List<String> argumentTypes,
      Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onAlterAggregate(
      String keyspace,
      String aggregate,
      List<String> argumentTypes,
      Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onDropKeyspace(String keyspace, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onDropTable(
      String keyspace, String table, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onDropType(
      String keyspace, String type, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onDropFunction(
      String keyspace,
      String function,
      List<String> argumentTypes,
      Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onDropAggregate(
      String keyspace,
      String aggregate,
      List<String> argumentTypes,
      Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onJoinCluster(
      InetAddress endpoint, int port, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onLeaveCluster(
      InetAddress endpoint, int port, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onUp(InetAddress endpoint, int port, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onDown(InetAddress endpoint, int port, Predicate<Map<String, String>> headerFilter) {}

  @Override
  public void onMove(InetAddress endpoint, int port, Predicate<Map<String, String>> headerFilter) {}
}
