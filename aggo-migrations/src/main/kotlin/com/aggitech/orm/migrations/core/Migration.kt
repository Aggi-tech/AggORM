package com.aggitech.orm.migrations.core

import java.security.MessageDigest

/**
 * Abstract base class for database migrations.
 *
 * Migrations must follow the naming pattern: V{version}_{timestamp}_{description}
 * Example: V001_20231201120000_CreateUsersTable
 *
 * Subclasses must implement:
 * - up(): Define the forward migration operations
 * - down(): Define the rollback operations
 */
abstract class Migration {

    /**
     * List of operations to execute during migration.
     * Populated by calling up() or down().
     */
    val operations = mutableListOf<MigrationOperation>()

    /**
     * Define the forward migration operations.
     * Add operations to the operations list.
     */
    abstract fun up()

    /**
     * Define the rollback operations.
     * Add operations to the operations list (in reverse order of up).
     */
    abstract fun down()

    /**
     * Extract the version number from the class name.
     * V001_20231201_Description -> 1
     */
    val version: Int by lazy {
        val className = this::class.simpleName ?: throw MigrationException("Invalid migration class name")
        val versionMatch = Regex("V(\\d+)_").find(className)
            ?: throw MigrationException("Migration class must start with V{version}_: $className")
        versionMatch.groupValues[1].toInt()
    }

    /**
     * Extract the timestamp from the class name.
     * V001_20231201120000_Description -> 20231201120000
     */
    val timestamp: Long by lazy {
        val className = this::class.simpleName ?: throw MigrationException("Invalid migration class name")
        val timestampMatch = Regex("V\\d+_(\\d+)_").find(className)
            ?: throw MigrationException("Migration class must have timestamp: V{version}_{timestamp}_: $className")
        timestampMatch.groupValues[1].toLong()
    }

    /**
     * Extract the description from the class name.
     * V001_20231201120000_CreateUsersTable -> CreateUsersTable
     */
    val description: String by lazy {
        val className = this::class.simpleName ?: throw MigrationException("Invalid migration class name")
        val descMatch = Regex("V\\d+_\\d+_(.+)").find(className)
            ?: throw MigrationException("Migration class must have description: V{version}_{timestamp}_{description}: $className")
        descMatch.groupValues[1]
    }

    /**
     * Calculate SHA-256 checksum of the migration operations.
     * Used to detect if a migration was modified after being applied.
     */
    fun checksum(): String {
        // Clear operations and repopulate to ensure consistency
        operations.clear()
        up()

        val content = operations.joinToString("\n") { it.toString() }
        val bytes = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // DSL helper methods for building migrations

    protected fun createTable(
        name: String,
        schema: String = "public",
        block: TableBuilder.() -> Unit
    ) {
        val builder = TableBuilder(name, schema)
        builder.block()
        operations.add(builder.build())
    }

    protected fun dropTable(name: String, schema: String = "public", ifExists: Boolean = true) {
        operations.add(MigrationOperation.DropTable(name, schema, ifExists))
    }

    protected fun renameTable(oldName: String, newName: String, schema: String = "public") {
        operations.add(MigrationOperation.RenameTable(oldName, newName, schema))
    }

    protected fun addColumn(tableName: String, schema: String = "public", block: ColumnBuilder.() -> Unit) {
        val builder = ColumnBuilder()
        builder.block()
        operations.add(MigrationOperation.AddColumn(tableName, schema, builder.build()))
    }

    protected fun dropColumn(tableName: String, columnName: String, schema: String = "public", ifExists: Boolean = false) {
        operations.add(MigrationOperation.DropColumn(tableName, columnName, schema, ifExists))
    }

    protected fun renameColumn(tableName: String, oldName: String, newName: String, schema: String = "public") {
        operations.add(MigrationOperation.RenameColumn(tableName, oldName, newName, schema))
    }

    protected fun createIndex(tableName: String, columns: List<String>, unique: Boolean = false, name: String? = null, schema: String = "public") {
        operations.add(MigrationOperation.CreateIndex(tableName, IndexDefinition(name, columns, unique), schema))
    }

    protected fun dropIndex(indexName: String, tableName: String? = null, schema: String = "public", ifExists: Boolean = false) {
        operations.add(MigrationOperation.DropIndex(indexName, tableName, schema, ifExists))
    }

    protected fun executeSql(sql: String) {
        operations.add(MigrationOperation.ExecuteSql(sql))
    }
}

/**
 * Builder for table creation
 */
class TableBuilder(private val name: String, private val schema: String) {
    private val columns = mutableListOf<ColumnDefinition>()
    private val primaryKeys = mutableListOf<String>()
    private val foreignKeys = mutableListOf<ForeignKeyDefinition>()
    private val indexes = mutableListOf<IndexDefinition>()
    private val uniqueConstraints = mutableListOf<UniqueConstraintDefinition>()

    fun column(block: ColumnBuilder.() -> Unit): ColumnDefinition {
        val builder = ColumnBuilder()
        builder.block()
        val column = builder.build()
        columns.add(column)
        if (column.primaryKey) {
            primaryKeys.add(column.name)
        }
        return column
    }

    fun primaryKey(vararg columnNames: String) {
        primaryKeys.addAll(columnNames)
    }

    fun foreignKey(columnName: String, referencedTable: String, referencedColumn: String, onDelete: CascadeType? = null, onUpdate: CascadeType? = null) {
        foreignKeys.add(ForeignKeyDefinition(null, columnName, referencedTable, referencedColumn, onDelete, onUpdate))
    }

    fun index(columns: List<String>, unique: Boolean = false, name: String? = null) {
        indexes.add(IndexDefinition(name, columns, unique))
    }

    fun unique(vararg columnNames: String, name: String? = null) {
        uniqueConstraints.add(UniqueConstraintDefinition(name, columnNames.toList()))
    }

    fun build(): MigrationOperation.CreateTable {
        return MigrationOperation.CreateTable(
            name = name,
            schema = schema,
            columns = columns,
            primaryKeys = primaryKeys,
            foreignKeys = foreignKeys,
            indexes = indexes,
            uniqueConstraints = uniqueConstraints
        )
    }
}

/**
 * Builder for column definitions
 */
class ColumnBuilder {
    private var name: String = ""
    private var type: com.aggitech.orm.migrations.dsl.ColumnType = com.aggitech.orm.migrations.dsl.ColumnType.Varchar(255)
    private var nullable: Boolean = true
    private var unique: Boolean = false
    private var primaryKey: Boolean = false
    private var autoIncrement: Boolean = false
    private var defaultValue: String? = null

    fun name(value: String) { name = value }
    fun type(value: com.aggitech.orm.migrations.dsl.ColumnType) { type = value }
    fun nullable(value: Boolean = true) { nullable = value }
    fun notNull(): ColumnBuilder { nullable = false; return this }
    fun unique(): ColumnBuilder { unique = true; return this }
    fun primaryKey(): ColumnBuilder { primaryKey = true; return this }
    fun autoIncrement(): ColumnBuilder { autoIncrement = true; return this }
    fun default(value: String): ColumnBuilder { defaultValue = value; return this }

    // Type helpers
    fun varchar(name: String, length: Int = 255): ColumnBuilder {
        this.name = name
        this.type = com.aggitech.orm.migrations.dsl.ColumnType.Varchar(length)
        return this
    }

    fun bigInteger(name: String): ColumnBuilder {
        this.name = name
        this.type = com.aggitech.orm.migrations.dsl.ColumnType.BigInteger
        return this
    }

    fun integer(name: String): ColumnBuilder {
        this.name = name
        this.type = com.aggitech.orm.migrations.dsl.ColumnType.Integer
        return this
    }

    fun timestamp(name: String): ColumnBuilder {
        this.name = name
        this.type = com.aggitech.orm.migrations.dsl.ColumnType.Timestamp
        return this
    }

    fun boolean(name: String): ColumnBuilder {
        this.name = name
        this.type = com.aggitech.orm.migrations.dsl.ColumnType.Boolean
        return this
    }

    fun text(name: String): ColumnBuilder {
        this.name = name
        this.type = com.aggitech.orm.migrations.dsl.ColumnType.Text
        return this
    }

    fun uuid(name: String): ColumnBuilder {
        this.name = name
        this.type = com.aggitech.orm.migrations.dsl.ColumnType.Uuid
        return this
    }

    fun build(): ColumnDefinition {
        if (name.isEmpty()) throw MigrationException("Column name is required")
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
