package de.akquinet.jbosscc.guttenbase.tools.schema.comparison;

import de.akquinet.jbosscc.guttenbase.hints.ColumnNameMapperHint;
import de.akquinet.jbosscc.guttenbase.hints.ColumnOrderHint;
import de.akquinet.jbosscc.guttenbase.hints.TableOrderHint;
import de.akquinet.jbosscc.guttenbase.mapping.ColumnMapper;
import de.akquinet.jbosscc.guttenbase.mapping.ColumnMapper.ColumnMapperResult;
import de.akquinet.jbosscc.guttenbase.mapping.ColumnNameMapper;
import de.akquinet.jbosscc.guttenbase.mapping.ColumnTypeMapping;
import de.akquinet.jbosscc.guttenbase.mapping.TableMapper;
import de.akquinet.jbosscc.guttenbase.meta.*;
import de.akquinet.jbosscc.guttenbase.repository.ConnectorRepository;
import de.akquinet.jbosscc.guttenbase.tools.CommonColumnTypeResolverTool;

import java.sql.SQLException;
import java.util.List;

/**
 * Will check two schemas for compatibility and report found issues.
 * <p>
 * &copy; 2012-2020 akquinet tech@spree
 * </p>
 *
 * @author M. Dahm
 * @Uses-Hint {@link ColumnNameMapperHint} to map column names
 * @Uses-Hint {@link TableOrderHint} to determine order of tables
 */
public class SchemaComparatorTool {
  private final ConnectorRepository _connectorRepository;
  private final SchemaCompatibilityIssues _schemaCompatibilityIssues = new SchemaCompatibilityIssues();

  public SchemaComparatorTool(final ConnectorRepository connectorRepository) {
    assert connectorRepository != null : "connectorRepository != null";
    _connectorRepository = connectorRepository;
  }

  /**
   * Check compatibility of both connectors/schemata.
   *
   * @return List of found issues. If empty the schemas are completely compatible
   * @throws SQLException
   */
  public SchemaCompatibilityIssues check(final String sourceConnectorId, final String targetConnectorId) throws SQLException {
    final List<TableMetaData> sourceTables = TableOrderHint.getSortedTables(_connectorRepository, sourceConnectorId);
    final TableMapper tableMapper = _connectorRepository.getConnectorHint(targetConnectorId, TableMapper.class).getValue();
    final DatabaseMetaData targetDatabase = _connectorRepository.getDatabaseMetaData(targetConnectorId);

    checkEqualTables(sourceTables, targetDatabase, tableMapper);

    for (final TableMetaData sourceTable : sourceTables) {
      final TableMetaData targetTable = tableMapper.map(sourceTable, targetDatabase);

      if (targetTable != null) {
        checkEqualColumns(sourceConnectorId, targetConnectorId, sourceTable, targetTable);
        checkEqualForeignKeys(sourceTable, targetTable);
        checkEqualIndexes(sourceTable, targetTable);
      }
    }

    return _schemaCompatibilityIssues;
  }

  public SchemaCompatibilityIssues checkEqualForeignKeys(final TableMetaData sourceTable, final TableMetaData targetTable) throws SQLException {
    for (final ForeignKeyMetaData sourceFK : sourceTable.getImportedForeignKeys()) {
      ForeignKeyMetaData matchingFK = null;

      for (final ForeignKeyMetaData targetFK : targetTable.getImportedForeignKeys()) {
        if (sourceFK.getReferencedColumn().equals(targetFK.getReferencedColumn()) &&
                sourceFK.getTableMetaData().equals(targetFK.getTableMetaData()) &&
                sourceFK.getReferencingColumn().equals(targetFK.getReferencingColumn())) {
          matchingFK = targetFK;
        }
      }

      if (matchingFK == null) {
        _schemaCompatibilityIssues.addIssue(new MissingForeignKeyIssue("Missing/incompatible foreign key " + sourceFK, sourceFK));
      }
    }


    return _schemaCompatibilityIssues;
  }

  public SchemaCompatibilityIssues checkEqualIndexes(final TableMetaData sourceTable, final TableMetaData targetTable) throws SQLException {
    for (final IndexMetaData sourceIndex : sourceTable.getIndexes()) {
      IndexMetaData matchingIndex = null;

      for (final IndexMetaData targetIndex : targetTable.getIndexes()) {
        if (sourceIndex.getColumnMetaData().equals(targetIndex.getColumnMetaData())) {
          matchingIndex = targetIndex;
        }
      }

      if (matchingIndex == null) {
        _schemaCompatibilityIssues.addIssue(new MissingIndexIssue("Missing index " + sourceIndex, sourceIndex));
      }
    }


    return _schemaCompatibilityIssues;
  }

  public SchemaCompatibilityIssues checkEqualColumns(final String sourceConnectorId, final String targetConnectorId,
                                                     final TableMetaData tableMetaData1, final TableMetaData tableMetaData2) throws SQLException {
    final ColumnMapper columnMapper = _connectorRepository.getConnectorHint(targetConnectorId, ColumnMapper.class).getValue();
    final CommonColumnTypeResolverTool commonColumnTypeResolver = new CommonColumnTypeResolverTool(_connectorRepository);
    final ColumnNameMapper sourceColumnNameMapper = _connectorRepository.getConnectorHint(sourceConnectorId,
            ColumnNameMapper.class).getValue();
    final ColumnNameMapper targetColumnNameMapper = _connectorRepository.getConnectorHint(targetConnectorId,
            ColumnNameMapper.class).getValue();

    final String tableName = tableMetaData1.getTableName();
    final List<ColumnMetaData> sourceColumns = ColumnOrderHint.getSortedColumns(_connectorRepository, sourceConnectorId,
            tableMetaData1);

    for (final ColumnMetaData sourceColumn : sourceColumns) {
      final ColumnMapperResult mapping = columnMapper.map(sourceColumn, tableMetaData2);
      final List<ColumnMetaData> targetColumns = mapping.getColumns();
      final String sourceColumnName = sourceColumnNameMapper.mapColumnName(sourceColumn);

      if (targetColumns.isEmpty()) {
        if (mapping.isEmptyColumnListOk()) {
          _schemaCompatibilityIssues.addIssue(new DroppedColumnIssue("No mapping column(s) found for: " + tableName + ":" + sourceColumn + " -> Will be dropped", sourceColumn));
        } else {
          _schemaCompatibilityIssues.addIssue(new MissingColumnIssue("No mapping column(s) found for: " + tableName + ":" + sourceColumn, sourceColumn));
        }
      }

      for (final ColumnMetaData targetColumn : targetColumns) {
        final String targetColumnName = targetColumnNameMapper.mapColumnName(targetColumn);
        final ColumnTypeMapping columnTypeMapping = commonColumnTypeResolver.getCommonColumnTypeMapping(sourceConnectorId,
                sourceColumn, targetConnectorId, targetColumn);

        if (columnTypeMapping == null) {
          _schemaCompatibilityIssues.addIssue(new IncompatibleColumnsIssue(
                  tableName + ":"
                          + sourceColumn
                          + ": Columns have incompatible types: "
                          + sourceColumnName
                          + "/"
                          + sourceColumn.getColumnTypeName()
                          + "/"
                          + sourceColumn.getColumnClassName()
                          + " vs. "
                          + targetColumnName
                          + "/"
                          + targetColumn.getColumnTypeName()
                          + "/"
                          + targetColumn.getColumnClassName(), sourceColumn, targetColumn));
        }
      }
    }

    return _schemaCompatibilityIssues;
  }

  private void checkEqualTables(final List<TableMetaData> sourceTableMetaData, final DatabaseMetaData targetDatabaseMetaData,
                                final TableMapper tableMapper) throws SQLException {
    for (final TableMetaData tableMetaData : sourceTableMetaData) {
      final TableMetaData targetTableMetaData = tableMapper.map(tableMetaData, targetDatabaseMetaData);

      if (targetTableMetaData == null) {
        _schemaCompatibilityIssues.addIssue(new MissingTableIssue("Table " + sourceTableMetaData + " is unknown/unmapped in target schema", tableMetaData));
      }
    }
  }
}
