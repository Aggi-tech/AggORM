package com.aggitech.orm.migrations.core

import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.migrations.dsl.ColumnType

/**
 * Supporting data classes for migration operations
 */
data class ColumnDefinition(
    val name: String,
    val type: ColumnType,
    val nullable: Boolean = true,
    val unique: Boolean = false,
    val primaryKey: Boolean = false,
    val autoIncrement: Boolean = false,
    val defaultValue: String? = null,
    val length: Int? = null,
    val precision: Int? = null,
    val scale: Int? = null
)

data class ForeignKeyDefinition(
    val name: String? = null,
    val columnName: String,
    val referencedTable: String,
    val referencedColumn: String,
    val onDelete: CascadeType? = null,
    val onUpdate: CascadeType? = null
)

data class IndexDefinition(
    val name: String? = null,
    val columns: List<String>,
    val unique: Boolean = false
)

data class UniqueConstraintDefinition(
    val name: String? = null,
    val columns: List<String>
)

enum class CascadeType {
    NO_ACTION,
    CASCADE,
    SET_NULL,
    SET_DEFAULT,
    RESTRICT
}

/**
 * Sealed class representing all possible migration operations.
 * Each operation can be converted to SQL via toSql() method.
 */
sealed class MigrationOperation {

    /**
     * Convert this operation to database-specific SQL statements.
     */
    abstract fun toSql(dialect: SqlDialect): List<String>

    // Table Operations
    data class CreateTable(
        val name: String,
        val schema: String = "public",
        val columns: List<ColumnDefinition>,
        val primaryKeys: List<String> = emptyList(),
        val foreignKeys: List<ForeignKeyDefinition> = emptyList(),
        val indexes: List<IndexDefinition> = emptyList(),
        val uniqueConstraints: List<UniqueConstraintDefinition> = emptyList()
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    data class DropTable(
        val name: String,
        val schema: String = "public",
        val ifExists: Boolean = true
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    data class RenameTable(
        val oldName: String,
        val newName: String,
        val schema: String = "public"
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    // Column Operations
    data class AddColumn(
        val tableName: String,
        val schema: String = "public",
        val column: ColumnDefinition
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    data class DropColumn(
        val tableName: String,
        val columnName: String,
        val schema: String = "public",
        val ifExists: Boolean = false
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    data class AlterColumn(
        val tableName: String,
        val columnName: String,
        val newType: ColumnType,
        val nullable: Boolean? = null,
        val defaultValue: String? = null,
        val schema: String = "public"
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    data class RenameColumn(
        val tableName: String,
        val oldName: String,
        val newName: String,
        val schema: String = "public"
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    // Constraint Operations
    data class AddPrimaryKey(
        val tableName: String,
        val columns: List<String>,
        val schema: String = "public"
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    data class DropPrimaryKey(
        val tableName: String,
        val schema: String = "public"
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    data class AddForeignKey(
        val tableName: String,
        val foreignKey: ForeignKeyDefinition,
        val schema: String = "public"
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    data class DropForeignKey(
        val tableName: String,
        val constraintName: String,
        val schema: String = "public",
        val ifExists: Boolean = false
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    // Index Operations
    data class CreateIndex(
        val tableName: String,
        val index: IndexDefinition,
        val schema: String = "public"
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    data class DropIndex(
        val indexName: String,
        val tableName: String? = null,
        val schema: String = "public",
        val ifExists: Boolean = false
    ) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> {
            throw UnsupportedOperationException("Use MigrationRenderer to generate SQL")
        }
    }

    // Raw SQL
    data class ExecuteSql(val sql: String) : MigrationOperation() {
        override fun toSql(dialect: SqlDialect): List<String> = listOf(sql)
    }
}

/**
 * Chainable extension methods for ColumnDefinition
 * Allows fluent API: uuid(User::id).primaryKey().notNull()
 */
fun ColumnDefinition.notNull(): ColumnDefinition = copy(nullable = false)
fun ColumnDefinition.nullable(): ColumnDefinition = copy(nullable = true)
fun ColumnDefinition.unique(): ColumnDefinition = copy(unique = true)
fun ColumnDefinition.primaryKey(): ColumnDefinition = copy(primaryKey = true)
fun ColumnDefinition.autoIncrement(): ColumnDefinition = copy(autoIncrement = true)
fun ColumnDefinition.default(value: String): ColumnDefinition = copy(defaultValue = value)
