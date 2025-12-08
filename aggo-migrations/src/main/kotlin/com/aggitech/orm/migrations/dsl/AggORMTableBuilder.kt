package com.aggitech.orm.migrations.dsl

import com.aggitech.orm.migrations.core.*
import com.aggitech.orm.migrations.meta.ColumnMeta
import com.aggitech.orm.migrations.meta.TableMeta
import kotlin.reflect.KProperty1

/**
 * Builder para alteracao de tabelas em migrations.
 * Suporta 3 formatos de referencia a colunas:
 *
 * 1. TableMeta (recomendado para colunas que podem ser removidas da entidade):
 *    ```kotlin
 *    table(UsersTable) {
 *        drop(UsersTable.OLD_FIELD)
 *    }
 *    ```
 *
 * 2. Strings (flexivel, funciona sempre):
 *    ```kotlin
 *    table("users") {
 *        dropColumn("old_field")
 *    }
 *    ```
 *
 * 3. KProperty (type-safe, mas requer propriedade existente na entidade):
 *    ```kotlin
 *    table("users") {
 *        drop(User::oldField)
 *    }
 *    ```
 */
class AggORMTableBuilder(
    private val tableName: String,
    private val schema: String = "public"
) {
    internal val operations = mutableListOf<MigrationOperation>()

    // ==================== FORMATO 1: ColumnMeta ====================

    /**
     * Adiciona uma coluna usando ColumnMeta
     */
    fun add(column: ColumnMeta) {
        operations.add(
            MigrationOperation.AddColumn(
                tableName = tableName,
                schema = schema,
                column = column.toColumnDefinition()
            )
        )
    }

    /**
     * Remove uma coluna usando ColumnMeta
     */
    fun drop(column: ColumnMeta) {
        operations.add(
            MigrationOperation.DropColumn(
                tableName = tableName,
                columnName = column.name,
                schema = schema
            )
        )
    }

    /**
     * Cria um indice usando ColumnMeta
     */
    fun index(vararg columns: ColumnMeta, name: String? = null, unique: Boolean = false) {
        val columnNames = columns.map { it.name }
        operations.add(
            MigrationOperation.CreateIndex(
                tableName = tableName,
                schema = schema,
                index = IndexDefinition(
                    name = name,
                    columns = columnNames,
                    unique = unique
                )
            )
        )
    }

    /**
     * Cria uma foreign key usando ColumnMeta
     */
    fun foreign(
        column: ColumnMeta,
        references: ColumnMeta,
        onDelete: CascadeType? = null,
        onUpdate: CascadeType? = null
    ) {
        val refTableName = references.tableName
            ?: throw MigrationException("Referenced column must belong to a TableMeta")

        operations.add(
            MigrationOperation.AddForeignKey(
                tableName = tableName,
                schema = schema,
                foreignKey = ForeignKeyDefinition(
                    name = "fk_${tableName}_${column.name}",
                    columnName = column.name,
                    referencedTable = refTableName,
                    referencedColumn = references.name,
                    onDelete = onDelete,
                    onUpdate = onUpdate
                )
            )
        )
    }

    // ==================== FORMATO 2: Strings ====================

    /**
     * Adiciona uma coluna usando string e builder
     */
    fun addColumn(name: String, block: ColumnBuilder.() -> Unit) {
        val builder = ColumnBuilder()
        builder.block()
        val columnDef = builder.buildWithName(name)
        operations.add(
            MigrationOperation.AddColumn(
                tableName = tableName,
                schema = schema,
                column = columnDef
            )
        )
    }

    /**
     * Remove uma coluna usando string
     */
    fun dropColumn(name: String, ifExists: Boolean = false) {
        operations.add(
            MigrationOperation.DropColumn(
                tableName = tableName,
                columnName = name,
                schema = schema,
                ifExists = ifExists
            )
        )
    }

    /**
     * Renomeia uma coluna
     */
    fun renameColumn(from: String, to: String) {
        operations.add(
            MigrationOperation.RenameColumn(
                tableName = tableName,
                oldName = from,
                newName = to,
                schema = schema
            )
        )
    }

    /**
     * Cria um indice usando strings
     */
    fun index(vararg columns: String, name: String? = null, unique: Boolean = false) {
        operations.add(
            MigrationOperation.CreateIndex(
                tableName = tableName,
                schema = schema,
                index = IndexDefinition(
                    name = name,
                    columns = columns.toList(),
                    unique = unique
                )
            )
        )
    }

    /**
     * Remove um indice
     */
    fun dropIndex(name: String, ifExists: Boolean = false) {
        operations.add(
            MigrationOperation.DropIndex(
                indexName = name,
                tableName = tableName,
                schema = schema,
                ifExists = ifExists
            )
        )
    }

    /**
     * Cria uma foreign key usando strings
     */
    fun foreign(
        column: String,
        refTable: String,
        refColumn: String,
        onDelete: CascadeType? = null,
        onUpdate: CascadeType? = null
    ) {
        operations.add(
            MigrationOperation.AddForeignKey(
                tableName = tableName,
                schema = schema,
                foreignKey = ForeignKeyDefinition(
                    name = "fk_${tableName}_$column",
                    columnName = column,
                    referencedTable = refTable,
                    referencedColumn = refColumn,
                    onDelete = onDelete,
                    onUpdate = onUpdate
                )
            )
        )
    }

    /**
     * Remove uma foreign key
     */
    fun dropForeign(name: String, ifExists: Boolean = false) {
        operations.add(
            MigrationOperation.DropForeignKey(
                tableName = tableName,
                constraintName = name,
                schema = schema,
                ifExists = ifExists
            )
        )
    }

    // ==================== FORMATO 3: KProperty ====================

    /**
     * Adiciona uma coluna usando KProperty
     */
    fun <T, R> add(property: KProperty1<T, R>, block: ColumnBuilder.() -> Unit) {
        val columnName = PropertyUtils.getColumnName(property)
        val builder = ColumnBuilder()
        builder.block()
        val columnDef = builder.buildWithName(columnName)
        operations.add(
            MigrationOperation.AddColumn(
                tableName = tableName,
                schema = schema,
                column = columnDef
            )
        )
    }

    /**
     * Remove uma coluna usando KProperty
     */
    fun <T, R> drop(property: KProperty1<T, R>) {
        val columnName = PropertyUtils.getColumnName(property)
        operations.add(
            MigrationOperation.DropColumn(
                tableName = tableName,
                columnName = columnName,
                schema = schema
            )
        )
    }

    /**
     * Cria um indice usando KProperty
     */
    fun <T> index(vararg properties: KProperty1<T, *>, name: String? = null, unique: Boolean = false) {
        val columnNames = properties.map { PropertyUtils.getColumnName(it) }
        operations.add(
            MigrationOperation.CreateIndex(
                tableName = tableName,
                schema = schema,
                index = IndexDefinition(
                    name = name,
                    columns = columnNames,
                    unique = unique
                )
            )
        )
    }

    /**
     * Cria uma foreign key usando KProperty
     */
    fun <T, U, R> foreign(
        source: KProperty1<T, R>,
        target: KProperty1<U, R>,
        targetTable: String,
        onDelete: CascadeType? = null,
        onUpdate: CascadeType? = null
    ) {
        val sourceColumn = PropertyUtils.getColumnName(source)
        val targetColumn = PropertyUtils.getColumnName(target)
        operations.add(
            MigrationOperation.AddForeignKey(
                tableName = tableName,
                schema = schema,
                foreignKey = ForeignKeyDefinition(
                    name = "fk_${tableName}_$sourceColumn",
                    columnName = sourceColumn,
                    referencedTable = targetTable,
                    referencedColumn = targetColumn,
                    onDelete = onDelete,
                    onUpdate = onUpdate
                )
            )
        )
    }

    // ==================== Helpers internos ====================

    /**
     * Converte ColumnMeta para ColumnDefinition
     */
    private fun ColumnMeta.toColumnDefinition(): ColumnDefinition {
        return ColumnDefinition(
            name = name,
            type = type,
            nullable = nullable,
            unique = unique,
            primaryKey = primaryKey,
            autoIncrement = autoIncrement,
            defaultValue = defaultValue
        )
    }

    /**
     * Builder auxiliar para definir tipos de coluna
     */
    class ColumnBuilder {
        private var type: ColumnType = ColumnType.Varchar(255)
        private var nullable: Boolean = true
        private var unique: Boolean = false
        private var primaryKey: Boolean = false
        private var autoIncrement: Boolean = false
        private var defaultValue: String? = null

        fun varchar(length: Int = 255): ColumnBuilder { type = ColumnType.Varchar(length); return this }
        fun char(length: Int): ColumnBuilder { type = ColumnType.Char(length); return this }
        fun text(): ColumnBuilder { type = ColumnType.Text; return this }
        fun integer(): ColumnBuilder { type = ColumnType.Integer; return this }
        fun bigint(): ColumnBuilder { type = ColumnType.BigInteger; return this }
        fun smallint(): ColumnBuilder { type = ColumnType.SmallInteger; return this }
        fun boolean(): ColumnBuilder { type = ColumnType.Boolean; return this }
        fun timestamp(): ColumnBuilder { type = ColumnType.Timestamp; return this }
        fun date(): ColumnBuilder { type = ColumnType.Date; return this }
        fun time(): ColumnBuilder { type = ColumnType.Time; return this }
        fun decimal(precision: Int, scale: Int): ColumnBuilder { type = ColumnType.Decimal(precision, scale); return this }
        fun float(): ColumnBuilder { type = ColumnType.Float; return this }
        fun double(): ColumnBuilder { type = ColumnType.Double; return this }
        fun uuid(): ColumnBuilder { type = ColumnType.Uuid; return this }
        fun json(): ColumnBuilder { type = ColumnType.Json; return this }
        fun jsonb(): ColumnBuilder { type = ColumnType.Jsonb; return this }
        fun binary(length: Int? = null): ColumnBuilder { type = ColumnType.Binary(length); return this }
        fun blob(): ColumnBuilder { type = ColumnType.Blob; return this }

        fun nullable(): ColumnBuilder { nullable = true; return this }
        fun notNull(): ColumnBuilder { nullable = false; return this }
        fun unique(): ColumnBuilder { unique = true; return this }
        fun primaryKey(): ColumnBuilder { primaryKey = true; return this }
        fun autoIncrement(): ColumnBuilder { autoIncrement = true; return this }
        fun default(value: String): ColumnBuilder { defaultValue = value; return this }

        internal fun buildWithName(name: String): ColumnDefinition {
            return ColumnDefinition(
                name = name,
                type = type,
                nullable = nullable,
                unique = unique,
                primaryKey = primaryKey,
                autoIncrement = autoIncrement,
                defaultValue = defaultValue
            )
        }
    }

    fun build(): List<MigrationOperation> = operations.toList()
}

/**
 * Builder para criacao de tabelas usando TableMeta
 */
class AggORMCreateTableBuilder(
    private val tableMeta: TableMeta
) {
    private val columns = mutableListOf<ColumnDefinition>()
    private val primaryKeys = mutableListOf<String>()
    private val foreignKeys = mutableListOf<ForeignKeyDefinition>()
    private val indexes = mutableListOf<IndexDefinition>()
    private val uniqueConstraints = mutableListOf<UniqueConstraintDefinition>()

    /**
     * Adiciona uma coluna da TableMeta
     */
    fun column(columnMeta: ColumnMeta) {
        val colDef = ColumnDefinition(
            name = columnMeta.name,
            type = columnMeta.type,
            nullable = columnMeta.nullable,
            unique = columnMeta.unique,
            primaryKey = columnMeta.primaryKey,
            autoIncrement = columnMeta.autoIncrement,
            defaultValue = columnMeta.defaultValue
        )
        columns.add(colDef)
        if (columnMeta.primaryKey) {
            primaryKeys.add(columnMeta.name)
        }
        if (columnMeta.references != null) {
            foreignKeys.add(
                ForeignKeyDefinition(
                    name = "fk_${tableMeta.tableName}_${columnMeta.name}",
                    columnName = columnMeta.name,
                    referencedTable = columnMeta.references!!.table,
                    referencedColumn = columnMeta.references!!.column,
                    onDelete = columnMeta.references!!.onDelete?.let { CascadeType.valueOf(it) },
                    onUpdate = columnMeta.references!!.onUpdate?.let { CascadeType.valueOf(it) }
                )
            )
        }
    }

    /**
     * Adiciona todas as colunas da TableMeta
     */
    fun allColumns() {
        tableMeta.columnsMeta.forEach { column(it) }
    }

    fun build(): MigrationOperation.CreateTable {
        return MigrationOperation.CreateTable(
            name = tableMeta.tableName,
            schema = tableMeta.schema,
            columns = columns,
            primaryKeys = primaryKeys,
            foreignKeys = foreignKeys,
            indexes = indexes,
            uniqueConstraints = uniqueConstraints
        )
    }
}
