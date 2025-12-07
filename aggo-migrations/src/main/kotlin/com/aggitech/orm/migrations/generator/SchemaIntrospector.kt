package com.aggitech.orm.migrations.generator

import com.aggitech.orm.migrations.dsl.ColumnType
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.Types

/**
 * Le o schema atual do banco de dados via JDBC metadata.
 * Usado para gerar TableMeta automaticamente apos migrations.
 *
 * Exemplo de uso:
 * ```kotlin
 * val introspector = SchemaIntrospector(connection)
 * val schema = introspector.introspect("public")
 * schema.tables.forEach { table ->
 *     println("Table: ${table.name}")
 *     table.columns.forEach { col ->
 *         println("  - ${col.name}: ${col.type}")
 *     }
 * }
 * ```
 */
class SchemaIntrospector(private val connection: Connection) {

    companion object {
        private const val MIGRATION_HISTORY_TABLE = "aggo_migration_history"
    }

    /**
     * Faz introspection do schema do banco de dados
     */
    fun introspect(schemaName: String = "public"): DatabaseSchema {
        val metadata = connection.metaData
        val tables = mutableListOf<TableSchema>()

        val rs = metadata.getTables(null, schemaName, "%", arrayOf("TABLE"))
        while (rs.next()) {
            val tableName = rs.getString("TABLE_NAME")
            // Ignora a tabela de historico de migrations
            if (tableName.lowercase() != MIGRATION_HISTORY_TABLE) {
                tables.add(introspectTable(metadata, schemaName, tableName))
            }
        }
        rs.close()

        return DatabaseSchema(schemaName, tables)
    }

    private fun introspectTable(metadata: DatabaseMetaData, schema: String, tableName: String): TableSchema {
        val columns = introspectColumns(metadata, schema, tableName)
        val primaryKeys = introspectPrimaryKeys(metadata, schema, tableName)
        val foreignKeys = introspectForeignKeys(metadata, schema, tableName)
        val indexes = introspectIndexes(metadata, schema, tableName)

        // Marca colunas que sao primary key
        val pkColumnNames = primaryKeys.map { it.columnName }.toSet()
        val columnsWithPk = columns.map { col ->
            if (col.name in pkColumnNames) col.copy(primaryKey = true) else col
        }

        return TableSchema(
            name = tableName,
            schema = schema,
            columns = columnsWithPk,
            primaryKeys = primaryKeys,
            foreignKeys = foreignKeys,
            indexes = indexes
        )
    }

    private fun introspectColumns(metadata: DatabaseMetaData, schema: String, tableName: String): List<ColumnSchema> {
        val columns = mutableListOf<ColumnSchema>()

        val rs = metadata.getColumns(null, schema, tableName, "%")
        while (rs.next()) {
            val columnName = rs.getString("COLUMN_NAME")
            val dataType = rs.getInt("DATA_TYPE")
            val typeName = rs.getString("TYPE_NAME")
            val columnSize = rs.getInt("COLUMN_SIZE")
            val decimalDigits = rs.getInt("DECIMAL_DIGITS")
            val nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable
            val defaultValue = rs.getString("COLUMN_DEF")
            val isAutoIncrement = rs.getString("IS_AUTOINCREMENT") == "YES"

            val columnType = mapSqlTypeToColumnType(dataType, typeName, columnSize, decimalDigits)

            columns.add(
                ColumnSchema(
                    name = columnName,
                    type = columnType,
                    nullable = nullable,
                    primaryKey = false, // Sera preenchido depois
                    unique = false, // Sera verificado via indices
                    autoIncrement = isAutoIncrement,
                    defaultValue = defaultValue
                )
            )
        }
        rs.close()

        return columns
    }

    private fun introspectPrimaryKeys(metadata: DatabaseMetaData, schema: String, tableName: String): List<PrimaryKeySchema> {
        val pks = mutableListOf<PrimaryKeySchema>()

        val rs = metadata.getPrimaryKeys(null, schema, tableName)
        while (rs.next()) {
            pks.add(
                PrimaryKeySchema(
                    columnName = rs.getString("COLUMN_NAME"),
                    keySequence = rs.getShort("KEY_SEQ").toInt(),
                    constraintName = rs.getString("PK_NAME")
                )
            )
        }
        rs.close()

        return pks.sortedBy { it.keySequence }
    }

    private fun introspectForeignKeys(metadata: DatabaseMetaData, schema: String, tableName: String): List<ForeignKeySchema> {
        val fks = mutableListOf<ForeignKeySchema>()

        val rs = metadata.getImportedKeys(null, schema, tableName)
        while (rs.next()) {
            fks.add(
                ForeignKeySchema(
                    columnName = rs.getString("FKCOLUMN_NAME"),
                    referencedTable = rs.getString("PKTABLE_NAME"),
                    referencedColumn = rs.getString("PKCOLUMN_NAME"),
                    constraintName = rs.getString("FK_NAME"),
                    onDelete = mapDeleteRule(rs.getShort("DELETE_RULE")),
                    onUpdate = mapUpdateRule(rs.getShort("UPDATE_RULE"))
                )
            )
        }
        rs.close()

        return fks
    }

    private fun introspectIndexes(metadata: DatabaseMetaData, schema: String, tableName: String): List<IndexSchema> {
        val indexMap = mutableMapOf<String, MutableList<IndexColumnSchema>>()
        val uniqueMap = mutableMapOf<String, Boolean>()

        val rs = metadata.getIndexInfo(null, schema, tableName, false, false)
        while (rs.next()) {
            val indexName = rs.getString("INDEX_NAME") ?: continue
            val columnName = rs.getString("COLUMN_NAME") ?: continue
            val nonUnique = rs.getBoolean("NON_UNIQUE")
            val ordinal = rs.getShort("ORDINAL_POSITION").toInt()

            indexMap.getOrPut(indexName) { mutableListOf() }.add(
                IndexColumnSchema(columnName, ordinal)
            )
            uniqueMap[indexName] = !nonUnique
        }
        rs.close()

        return indexMap.map { (name, columns) ->
            IndexSchema(
                name = name,
                columns = columns.sortedBy { it.ordinal }.map { it.columnName },
                unique = uniqueMap[name] ?: false
            )
        }
    }

    private fun mapSqlTypeToColumnType(sqlType: Int, typeName: String, size: Int, decimalDigits: Int): ColumnType {
        return when (sqlType) {
            Types.VARCHAR, Types.NVARCHAR, Types.LONGVARCHAR -> ColumnType.Varchar(size)
            Types.CHAR, Types.NCHAR -> ColumnType.Char(size)
            Types.CLOB, Types.NCLOB, Types.LONGNVARCHAR -> ColumnType.Text
            Types.INTEGER -> ColumnType.Integer
            Types.BIGINT -> ColumnType.BigInteger
            Types.SMALLINT, Types.TINYINT -> ColumnType.SmallInteger
            Types.BOOLEAN, Types.BIT -> ColumnType.Boolean
            Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> ColumnType.Timestamp
            Types.DATE -> ColumnType.Date
            Types.TIME, Types.TIME_WITH_TIMEZONE -> ColumnType.Time
            Types.DECIMAL, Types.NUMERIC -> ColumnType.Decimal(size, decimalDigits)
            Types.FLOAT, Types.REAL -> ColumnType.Float
            Types.DOUBLE -> ColumnType.Double
            Types.BINARY, Types.VARBINARY -> ColumnType.Binary(size)
            Types.BLOB, Types.LONGVARBINARY -> ColumnType.Blob
            Types.OTHER -> {
                // Tipos especificos do PostgreSQL
                when (typeName.lowercase()) {
                    "uuid" -> ColumnType.Uuid
                    "json" -> ColumnType.Json
                    "jsonb" -> ColumnType.Jsonb
                    else -> ColumnType.Varchar(255) // Fallback
                }
            }
            else -> ColumnType.Varchar(255) // Fallback
        }
    }

    private fun mapDeleteRule(rule: Short): String? {
        return when (rule.toInt()) {
            DatabaseMetaData.importedKeyCascade -> "CASCADE"
            DatabaseMetaData.importedKeySetNull -> "SET_NULL"
            DatabaseMetaData.importedKeySetDefault -> "SET_DEFAULT"
            DatabaseMetaData.importedKeyRestrict -> "RESTRICT"
            DatabaseMetaData.importedKeyNoAction -> "NO_ACTION"
            else -> null
        }
    }

    private fun mapUpdateRule(rule: Short): String? = mapDeleteRule(rule)
}

// ==================== Data Classes para Schema ====================

/**
 * Representa o schema completo do banco de dados
 */
data class DatabaseSchema(
    val name: String,
    val tables: List<TableSchema>
)

/**
 * Representa uma tabela do banco de dados
 */
data class TableSchema(
    val name: String,
    val schema: String,
    val columns: List<ColumnSchema>,
    val primaryKeys: List<PrimaryKeySchema>,
    val foreignKeys: List<ForeignKeySchema>,
    val indexes: List<IndexSchema>
)

/**
 * Representa uma coluna do banco de dados
 */
data class ColumnSchema(
    val name: String,
    val type: ColumnType,
    val nullable: Boolean,
    val primaryKey: Boolean,
    val unique: Boolean,
    val autoIncrement: Boolean,
    val defaultValue: String?
)

/**
 * Representa uma primary key
 */
data class PrimaryKeySchema(
    val columnName: String,
    val keySequence: Int,
    val constraintName: String?
)

/**
 * Representa uma foreign key
 */
data class ForeignKeySchema(
    val columnName: String,
    val referencedTable: String,
    val referencedColumn: String,
    val constraintName: String?,
    val onDelete: String?,
    val onUpdate: String?
)

/**
 * Representa um indice
 */
data class IndexSchema(
    val name: String,
    val columns: List<String>,
    val unique: Boolean
)

private data class IndexColumnSchema(
    val columnName: String,
    val ordinal: Int
)
