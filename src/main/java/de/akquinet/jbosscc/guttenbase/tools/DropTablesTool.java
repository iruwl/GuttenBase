package de.akquinet.jbosscc.guttenbase.tools;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import de.akquinet.jbosscc.guttenbase.connector.ConnectorInfo;
import de.akquinet.jbosscc.guttenbase.hints.TableOrderHint;
import de.akquinet.jbosscc.guttenbase.mapping.TableNameMapper;
import de.akquinet.jbosscc.guttenbase.meta.ForeignKeyMetaData;
import de.akquinet.jbosscc.guttenbase.meta.IndexMetaData;
import de.akquinet.jbosscc.guttenbase.meta.TableMetaData;
import de.akquinet.jbosscc.guttenbase.repository.ConnectorRepository;

/**
 * Will drop tables in given schema. USE WITH CARE!
 * <p>
 * &copy; 2012 akquinet tech@spree
 * </p>
 * 
 * @Uses-Hint {@link TableOrderHint} to determine order of tables
 * @author M. Dahm
 */
public class DropTablesTool
{
  final ConnectorRepository _connectorRepository;
  public final ScriptExecutorTool _scriptExecutor;

  public DropTablesTool(final ConnectorRepository connectorRepository)
  {
    assert connectorRepository != null : "connectorRepository != null";
    _connectorRepository = connectorRepository;
    _scriptExecutor = new ScriptExecutorTool(connectorRepository);
  }

  public void dropForeignKeys(final String connectorId) throws SQLException
  {
    final List<TableMetaData> tableMetaData = TableOrderHint.getSortedTables(_connectorRepository, connectorId);
    final TableNameMapper tableNameMapper = _connectorRepository.getConnectorHint(connectorId, TableNameMapper.class).getValue();
    final List<String> statements = new ArrayList<String>();
    final ConnectorInfo connectionInfo = _connectorRepository.getConnectionInfo(connectorId);
    String constraintClause;

    switch (connectionInfo.getDatabaseType())
    {
    case MYSQL:
      constraintClause = " FOREIGN KEY ";
      break;
    default:
      constraintClause = " CONSTRAINT ";
      break;
    }

    for (final TableMetaData table : tableMetaData)
    {
      for (final ForeignKeyMetaData foreignKey : table.getImportedForeignKeys())
      {
        statements.add("ALTER TABLE " + tableNameMapper.mapTableName(table)
            + " DROP"
            + constraintClause
            + foreignKey.getForeignKeyName()
            + ";");
      }
    }

    if (!statements.isEmpty())
    {
      _scriptExecutor.executeScript(connectorId, true, false, statements);
    }
  }

  public void dropIndexes(final String connectorId) throws SQLException
  {
    final List<TableMetaData> tableMetaData = TableOrderHint.getSortedTables(_connectorRepository, connectorId);
    final List<String> statements = new ArrayList<String>();

    for (final TableMetaData table : tableMetaData)
    {
      String schema = table.getDatabaseMetaData().getSchema();

      if (!"".equals(schema))
      {
        schema += ".";
      }

      for (final IndexMetaData index : table.getIndexes())
      {
        if (!index.isPrimaryKeyIndex())
        {
          statements.add("DROP INDEX " + schema + index.getIndexName() + ";");
        }
      }
    }

    if (!statements.isEmpty())
    {
      _scriptExecutor.executeScript(connectorId, true, false, statements);
    }
  }

  public void dropTables(final String connectorId) throws SQLException
  {
    final List<TableMetaData> tableMetaData = TableOrderHint.getSortedTables(_connectorRepository, connectorId);
    final List<String> statements = new ArrayList<String>();
    final TableNameMapper tableNameMapper = _connectorRepository.getConnectorHint(connectorId, TableNameMapper.class).getValue();

    for (final TableMetaData table : tableMetaData)
    {
      statements.add("DROP TABLE " + tableNameMapper.mapTableName(table) + ";");
    }

    if (!statements.isEmpty())
    {
      _scriptExecutor.executeScript(connectorId, true, true, statements);
    }
  }
}
