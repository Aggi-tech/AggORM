package com.aggitech.orm.migrations.renderer

import com.aggitech.orm.enums.PostgresDialect
import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.migrations.core.*
import com.aggitech.orm.migrations.dsl.ColumnType

/**
 * PostgreSQL-specific migration renderer.
 * Generates PostgreSQL-compatible SQL statements from migration operations.
 */
class PostgresMigrationRenderer : MigrationRenderer {

    override val dialect: SqlDialect = PostgresDialect

    override fun render(operation: MigrationOperation): List<String> {
        return when (operation) {
            is MigrationOperation.CreateTable -> renderCreateTable(operation)
            is MigrationOperation.DropTable -> renderDropTable(operation)
            is MigrationOperation.RenameTable -> renderRenameTable(operation)
            is MigrationOperation.AddColumn -> renderAddColumn(operation)
            is MigrationOperation.DropColumn -> renderDropColumn(operation)
            is MigrationOperation.AlterColumn -> renderAlterColumn(operation)
            is MigrationOperation.RenameColumn -> renderRenameColumn(operation)
            is MigrationOperation.AddPrimaryKey -> renderAddPrimaryKey(operation)
            is MigrationOperation.DropPrimaryKey -> renderDropPrimaryKey(operation)
            is MigrationOperation.AddForeignKey -> renderAddForeignKey(operation)
            is MigrationOperation.DropForeignKey -> renderDropForeignKey(operation)
            is MigrationOperation.CreateIndex -> renderCreateIndex(operation)
            is MigrationOperation.DropIndex -> renderDropIndex(operation)
            is MigrationOperation.ExecuteSql -> listOf(operation.sql)
        }
    }

    private fun renderCreateTable(op: MigrationOperation.CreateTable): List<String> {
        val statements = mutableListOf<String>()

        // Create ENUM types first (if any)
        val enumTypes = op.columns
            .map { it.type }
            .filterIsInstance<ColumnType.Enum>()
            .distinctBy { it.typeName }

        enumTypes.forEach { enumType ->
            statements.add(renderCreateEnumType(enumType))
        }

        val tableName = qualifiedTableName(op.schema, op.name)
        val columnDefs = op.columns.joinToString(",\n  ") { renderColumnDefinition(it) }

        val constraints = mutableListOf<String>()

        // Primary key constraint
        if (op.primaryKeys.isNotEmpty()) {
            val pkColumns = op.primaryKeys.joinToString(", ") { quote(it) }
            constraints.add("PRIMARY KEY ($pkColumns)")
        }

        // Unique constraints
        op.uniqueConstraints.forEach { uc ->
            val ucColumns = uc.columns.joinToString(", ") { quote(it) }
            val constraintName = uc.name ?: "uk_${op.name}_${uc.columns.joinToString("_")}"
            constraints.add("CONSTRAINT ${quote(constraintName)} UNIQUE ($ucColumns)")
        }

        val allConstraints = if (constraints.isNotEmpty()) {
            ",\n  " + constraints.joinToString(",\n  ")
        } else ""

        statements.add("""
            CREATE TABLE $tableName (
              $columnDefs$allConstraints
            )
        """.trimIndent())

        // Foreign keys as separate ALTER TABLE statements (PostgreSQL best practice)
        op.foreignKeys.forEach { fk ->
            statements.add(renderAddForeignKeyStatement(op.schema, op.name, fk))
        }

        // Indexes as separate statements
        op.indexes.forEach { idx ->
            statements.add(renderCreateIndexStatement(op.schema, op.name, idx))
        }

        return statements
    }

    private fun renderColumnDefinition(col: ColumnDefinition): String {
        val parts = mutableListOf<String>()

        parts.add(quote(col.name))
        parts.add(mapColumnType(col.type))

        if (!col.nullable) parts.add("NOT NULL")
        if (col.unique) parts.add("UNIQUE")
        if (col.autoIncrement) parts.add("GENERATED ALWAYS AS IDENTITY")
        if (col.defaultValue != null) parts.add("DEFAULT ${col.defaultValue}")

        return parts.joinToString(" ")
    }

    private fun mapColumnType(type: ColumnType): String {
        return when (type) {
            is ColumnType.Varchar -> "VARCHAR(${type.length})"
            is ColumnType.Char -> "CHAR(${type.length})"
            is ColumnType.Text -> "TEXT"
            is ColumnType.Integer -> "INTEGER"
            is ColumnType.BigInteger -> "BIGINT"
            is ColumnType.SmallInteger -> "SMALLINT"
            is ColumnType.Serial -> "SERIAL"
            is ColumnType.BigSerial -> "BIGSERIAL"
            is ColumnType.Boolean -> "BOOLEAN"
            is ColumnType.Decimal -> "DECIMAL(${type.precision}, ${type.scale})"
            is ColumnType.Float -> "REAL"
            is ColumnType.Double -> "DOUBLE PRECISION"
            is ColumnType.Date -> "DATE"
            is ColumnType.Time -> "TIME"
            is ColumnType.Timestamp -> "TIMESTAMP"
            is ColumnType.TimestampTz -> "TIMESTAMPTZ"
            is ColumnType.Binary -> if (type.length != null) "BYTEA" else "BYTEA"
            is ColumnType.Blob -> "BYTEA"
            is ColumnType.Json -> "JSON"
            is ColumnType.Jsonb -> "JSONB"
            is ColumnType.Uuid -> "UUID"
            is ColumnType.Enum -> type.typeName
        }
    }

    private fun renderCreateEnumType(enumType: ColumnType.Enum): String {
        val values = enumType.values.joinToString(", ") { "'$it'" }
        return "DO $$ BEGIN CREATE TYPE ${enumType.typeName} AS ENUM ($values); EXCEPTION WHEN duplicate_object THEN null; END $$"
    }

    private fun renderDropTable(op: MigrationOperation.DropTable): List<String> {
        val tableName = qualifiedTableName(op.schema, op.name)
        val ifExists = if (op.ifExists) "IF EXISTS " else ""
        return listOf("DROP TABLE $ifExists$tableName")
    }

    private fun renderRenameTable(op: MigrationOperation.RenameTable): List<String> {
        val oldName = qualifiedTableName(op.schema, op.oldName)
        return listOf("ALTER TABLE $oldName RENAME TO ${quote(op.newName)}")
    }

    private fun renderAddColumn(op: MigrationOperation.AddColumn): List<String> {
        val tableName = qualifiedTableName(op.schema, op.tableName)
        val columnDef = renderColumnDefinition(op.column)
        return listOf("ALTER TABLE $tableName ADD COLUMN $columnDef")
    }

    private fun renderDropColumn(op: MigrationOperation.DropColumn): List<String> {
        val tableName = qualifiedTableName(op.schema, op.tableName)
        val ifExists = if (op.ifExists) "IF EXISTS " else ""
        return listOf("ALTER TABLE $tableName DROP COLUMN $ifExists${quote(op.columnName)}")
    }

    private fun renderAlterColumn(op: MigrationOperation.AlterColumn): List<String> {
        val statements = mutableListOf<String>()
        val tableName = qualifiedTableName(op.schema, op.tableName)
        val columnName = quote(op.columnName)

        // PostgreSQL requires separate ALTER statements for each change
        statements.add("ALTER TABLE $tableName ALTER COLUMN $columnName TYPE ${mapColumnType(op.newType)}")

        if (op.nullable != null) {
            val nullClause = if (op.nullable) "DROP NOT NULL" else "SET NOT NULL"
            statements.add("ALTER TABLE $tableName ALTER COLUMN $columnName $nullClause")
        }

        if (op.defaultValue != null) {
            statements.add("ALTER TABLE $tableName ALTER COLUMN $columnName SET DEFAULT ${op.defaultValue}")
        }

        return statements
    }

    private fun renderRenameColumn(op: MigrationOperation.RenameColumn): List<String> {
        val tableName = qualifiedTableName(op.schema, op.tableName)
        return listOf("ALTER TABLE $tableName RENAME COLUMN ${quote(op.oldName)} TO ${quote(op.newName)}")
    }

    private fun renderAddPrimaryKey(op: MigrationOperation.AddPrimaryKey): List<String> {
        val tableName = qualifiedTableName(op.schema, op.tableName)
        val constraintName = "pk_${op.tableName}"
        val columns = op.columns.joinToString(", ") { quote(it) }
        return listOf("ALTER TABLE $tableName ADD CONSTRAINT ${quote(constraintName)} PRIMARY KEY ($columns)")
    }

    private fun renderDropPrimaryKey(op: MigrationOperation.DropPrimaryKey): List<String> {
        val tableName = qualifiedTableName(op.schema, op.tableName)
        val constraintName = "pk_${op.tableName}"
        return listOf("ALTER TABLE $tableName DROP CONSTRAINT ${quote(constraintName)}")
    }

    private fun renderAddForeignKey(op: MigrationOperation.AddForeignKey): List<String> {
        return listOf(renderAddForeignKeyStatement(op.schema, op.tableName, op.foreignKey))
    }

    private fun renderAddForeignKeyStatement(schema: String, tableName: String, fk: ForeignKeyDefinition): String {
        val table = qualifiedTableName(schema, tableName)
        val constraintName = fk.name ?: "fk_${tableName}_${fk.columnName}"
        val referencedTable = qualifiedTableName(schema, fk.referencedTable)

        val onDelete = when (fk.onDelete) {
            CascadeType.CASCADE -> " ON DELETE CASCADE"
            CascadeType.SET_NULL -> " ON DELETE SET NULL"
            CascadeType.RESTRICT -> " ON DELETE RESTRICT"
            CascadeType.NO_ACTION -> " ON DELETE NO ACTION"
            CascadeType.SET_DEFAULT -> " ON DELETE SET DEFAULT"
            null -> ""
        }

        val onUpdate = when (fk.onUpdate) {
            CascadeType.CASCADE -> " ON UPDATE CASCADE"
            CascadeType.SET_NULL -> " ON UPDATE SET NULL"
            CascadeType.RESTRICT -> " ON UPDATE RESTRICT"
            CascadeType.NO_ACTION -> " ON UPDATE NO ACTION"
            CascadeType.SET_DEFAULT -> " ON UPDATE SET DEFAULT"
            null -> ""
        }

        return """
            ALTER TABLE $table
            ADD CONSTRAINT ${quote(constraintName)}
            FOREIGN KEY (${quote(fk.columnName)})
            REFERENCES $referencedTable (${quote(fk.referencedColumn)})$onDelete$onUpdate
        """.trimIndent().replace("\n", " ")
    }

    private fun renderDropForeignKey(op: MigrationOperation.DropForeignKey): List<String> {
        val tableName = qualifiedTableName(op.schema, op.tableName)
        val ifExists = if (op.ifExists) "IF EXISTS " else ""
        return listOf("ALTER TABLE $tableName DROP CONSTRAINT $ifExists${quote(op.constraintName)}")
    }

    private fun renderCreateIndex(op: MigrationOperation.CreateIndex): List<String> {
        return listOf(renderCreateIndexStatement(op.schema, op.tableName, op.index))
    }

    private fun renderCreateIndexStatement(schema: String, tableName: String, idx: IndexDefinition): String {
        val indexName = idx.name ?: "idx_${tableName}_${idx.columns.joinToString("_")}"
        val unique = if (idx.unique) "UNIQUE " else ""
        val table = qualifiedTableName(schema, tableName)
        val columns = idx.columns.joinToString(", ") { quote(it) }
        return "CREATE ${unique}INDEX ${quote(indexName)} ON $table ($columns)"
    }

    private fun renderDropIndex(op: MigrationOperation.DropIndex): List<String> {
        val ifExists = if (op.ifExists) "IF EXISTS " else ""
        val indexName = qualifiedTableName(op.schema, op.indexName)
        return listOf("DROP INDEX $ifExists$indexName")
    }

    private fun qualifiedTableName(schema: String, table: String): String {
        return if (schema.isNotEmpty() && schema != "public") {
            "${quote(schema)}.${quote(table)}"
        } else {
            quote(table)
        }
    }

    private fun quote(identifier: String): String {
        return "${dialect.quoteChar}$identifier${dialect.quoteChar}"
    }
}
