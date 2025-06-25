package io.stargate.db.cassandra.impl;

import io.stargate.db.EventListener;
import org.apache.cassandra.cql3.functions.UDAggregate;
import org.apache.cassandra.cql3.functions.UDFunction;
import org.apache.cassandra.db.marshal.UserType;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.SchemaChangeListener;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.schema.ViewMetadata;

public class EventListenerWrapper implements SchemaChangeListener {
  private final EventListener wrapped;

  EventListenerWrapper(EventListener wrapped) {
    this.wrapped = wrapped;
  }

  private java.util.List<String> convertArgTypes(
      java.util.List<org.apache.cassandra.db.marshal.AbstractType<?>> types) {
    return types.stream().map(t -> t.toString()).collect(java.util.stream.Collectors.toList());
  }

  @Override
  public void onCreateKeyspace(KeyspaceMetadata keyspace) {
    wrapped.onCreateKeyspace(keyspace.name);
  }

  @Override
  public void onCreateTable(TableMetadata table) {
    wrapped.onCreateTable(table.keyspace, table.name);
  }

  @Override
  public void onCreateView(ViewMetadata view) {
    wrapped.onCreateView(view.keyspace(), view.name());
  }

  @Override
  public void onCreateType(UserType type) {
    wrapped.onCreateType(type.keyspace, type.getNameAsString());
  }

  @Override
  public void onCreateFunction(UDFunction function) {
    wrapped.onCreateFunction(
        function.name().keyspace, function.name().name, convertArgTypes(function.argTypes()));
  }

  @Override
  public void onCreateAggregate(UDAggregate aggregate) {
    wrapped.onCreateAggregate(
        aggregate.name().keyspace, aggregate.name().name, convertArgTypes(aggregate.argTypes()));
  }

  @Override
  public void onAlterKeyspace(KeyspaceMetadata before, KeyspaceMetadata after) {
    wrapped.onAlterKeyspace(after.name);
  }

  @Override
  public void onAlterTable(TableMetadata before, TableMetadata after, boolean affectsStatements) {
    wrapped.onAlterTable(after.keyspace, after.name);
  }

  @Override
  public void onAlterView(ViewMetadata before, ViewMetadata after, boolean affectsStatements) {
    wrapped.onAlterView(after.keyspace(), after.name());
  }

  @Override
  public void onAlterType(UserType before, UserType after) {
    wrapped.onAlterType(after.keyspace, after.getNameAsString());
  }

  @Override
  public void onAlterFunction(UDFunction before, UDFunction after) {
    wrapped.onAlterFunction(
        after.name().keyspace, after.name().name, convertArgTypes(after.argTypes()));
  }

  @Override
  public void onAlterAggregate(UDAggregate before, UDAggregate after) {
    wrapped.onAlterAggregate(
        after.name().keyspace, after.name().name, convertArgTypes(after.argTypes()));
  }

  @Override
  public void onDropKeyspace(KeyspaceMetadata keyspace, boolean dropData) {
    wrapped.onDropKeyspace(keyspace.name);
  }

  @Override
  public void onDropTable(TableMetadata table, boolean dropData) {
    wrapped.onDropTable(table.keyspace, table.name);
  }

  @Override
  public void onDropView(ViewMetadata view, boolean dropData) {
    wrapped.onDropView(view.keyspace(), view.name());
  }

  @Override
  public void onDropType(UserType type) {
    wrapped.onDropType(type.keyspace, type.getNameAsString());
  }

  @Override
  public void onDropFunction(UDFunction function) {
    wrapped.onDropFunction(
        function.name().keyspace, function.name().name, convertArgTypes(function.argTypes()));
  }

  @Override
  public void onDropAggregate(UDAggregate aggregate) {
    wrapped.onDropAggregate(
        aggregate.name().keyspace, aggregate.name().name, convertArgTypes(aggregate.argTypes()));
  }
}
