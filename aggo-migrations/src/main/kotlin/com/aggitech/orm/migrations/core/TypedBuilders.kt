package com.aggitech.orm.migrations.core

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.migrations.dsl.PropertyUtils
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Type-safe builder for creating tables using entity classes
 *
 * Usage:
 * ```kotlin
 * createTable(User::class) {
 *     column { uuid(User::id).primaryKey() }
 *     column { varchar(User::name, 100).notNull() }
 *     foreignKey(Post::userId, User::id, onDelete = CascadeType.CASCADE)
 *     index(listOf(User::email), unique = true)
 * }
 * ```
 */
class TypedTableBuilder<T : Any>(
    private val entityClass: KClass<T>,
    private val tableName: String,
    private val schema: String
) {
    private val columns = mutableListOf<ColumnDefinition>()
    private val primaryKeys = mutableListOf<String>()
    private val foreignKeys = mutableListOf<ForeignKeyDefinition>()
    private val indexes = mutableListOf<IndexDefinition>()
    private val uniqueConstraints = mutableListOf<UniqueConstraintDefinition>()

    /**
     * Add a column using type-safe builder
     */
    fun column(block: TypedColumnBuilder<T>.() -> ColumnDefinition) {
        val builder = TypedColumnBuilder<T>()
        val columnDef = builder.block()
        columns.add(columnDef)

        if (columnDef.primaryKey) {
            primaryKeys.add(columnDef.name)
        }
    }

    /**
     * Add a foreign key constraint with type inference
     * Automatically infers referenced table and column from target property
     *
     * Example: foreignKey(Post::userId, User::id, onDelete = CascadeType.CASCADE)
     */
    fun <U : Any, R> foreignKey(
        sourceProperty: KProperty1<T, R>,
        targetProperty: KProperty1<U, R>,
        targetEntityClass: KClass<U>,
        onDelete: CascadeType? = null,
        onUpdate: CascadeType? = null
    ) {
        val sourceColumn = PropertyUtils.getColumnName(sourceProperty)

        // Infer referenced table and column from target property
        val targetTable = EntityRegistry.resolveTable(targetEntityClass)
        val targetColumn = PropertyUtils.getColumnName(targetProperty)

        foreignKeys.add(ForeignKeyDefinition(
            name = "fk_${tableName}_${sourceColumn}",
            columnName = sourceColumn,
            referencedTable = targetTable,
            referencedColumn = targetColumn,
            onDelete = onDelete,
            onUpdate = onUpdate
        ))
    }

    /**
     * Add an index on specified properties
     *
     * Example: index(listOf(Post::userId, Post::createdAt), name = "idx_posts_user_created")
     */
    fun index(properties: List<KProperty1<T, *>>, name: String? = null, unique: Boolean = false) {
        val columnNames = properties.map { PropertyUtils.getColumnName(it) }
        indexes.add(IndexDefinition(name, columnNames, unique))
    }

    /**
     * Add a unique constraint on specified properties
     *
     * Example: unique(User::email)
     */
    fun unique(vararg properties: KProperty1<T, *>, name: String? = null) {
        val columnNames = properties.map { PropertyUtils.getColumnName(it) }
        uniqueConstraints.add(UniqueConstraintDefinition(name, columnNames))
    }

    /**
     * Add explicit primary key (if not using .primaryKey() on column)
     */
    fun primaryKey(vararg properties: KProperty1<T, *>) {
        primaryKeys.addAll(properties.map { PropertyUtils.getColumnName(it) })
    }

    fun build(): MigrationOperation.CreateTable {
        return MigrationOperation.CreateTable(
            name = tableName,
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
 * Type-safe builder for updating existing tables
 *
 * Usage:
 * ```kotlin
 * transaction {
 *     updateTable(User::class) {
 *         column { varchar(User::phoneNumber, 20) }
 *         alterColumn(User::email).notNull()
 *         dropColumn(User::oldField)
 *     }
 * }
 * ```
 */
class TypedTableUpdateBuilder<T : Any>(
    private val entityClass: KClass<T>,
    private val tableName: String,
    private val schema: String
) {
    private val operations = mutableListOf<MigrationOperation>()

    /**
     * Add a new column to the table
     */
    fun column(block: TypedColumnBuilder<T>.() -> ColumnDefinition) {
        val builder = TypedColumnBuilder<T>()
        val columnDef = builder.block()
        operations.add(MigrationOperation.AddColumn(tableName, schema, columnDef))
    }

    /**
     * Drop a column from the table
     *
     * Example: dropColumn(User::oldField)
     */
    fun <R> dropColumn(property: KProperty1<T, R>) {
        val columnName = PropertyUtils.getColumnName(property)
        operations.add(MigrationOperation.DropColumn(tableName, columnName, schema, ifExists = false))
    }

    fun buildOperations(): List<MigrationOperation> = operations
}
