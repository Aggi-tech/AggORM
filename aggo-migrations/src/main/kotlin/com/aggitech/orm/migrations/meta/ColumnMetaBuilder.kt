package com.aggitech.orm.migrations.meta

import com.aggitech.orm.migrations.dsl.ColumnType

/**
 * Builder para criar ColumnMeta de forma programatica.
 * Usado internamente pelo TableMetaGenerator.
 *
 * Exemplo:
 * ```kotlin
 * val column = ColumnMetaBuilder("id", ColumnType.Uuid)
 *     .primaryKey()
 *     .notNull()
 *     .build()
 * ```
 */
class ColumnMetaBuilder(
    private val name: String,
    private val type: ColumnType
) {
    private var nullable: Boolean = true
    private var primaryKey: Boolean = false
    private var unique: Boolean = false
    private var autoIncrement: Boolean = false
    private var defaultValue: String? = null
    private var references: ForeignKeyMeta? = null

    fun notNull(): ColumnMetaBuilder {
        nullable = false
        return this
    }

    fun nullable(): ColumnMetaBuilder {
        nullable = true
        return this
    }

    fun primaryKey(): ColumnMetaBuilder {
        primaryKey = true
        return this
    }

    fun unique(): ColumnMetaBuilder {
        unique = true
        return this
    }

    fun autoIncrement(): ColumnMetaBuilder {
        autoIncrement = true
        return this
    }

    fun default(value: String): ColumnMetaBuilder {
        defaultValue = value
        return this
    }

    fun references(table: String, column: String, onDelete: String? = null, onUpdate: String? = null): ColumnMetaBuilder {
        references = ForeignKeyMeta(table, column, onDelete, onUpdate)
        return this
    }

    fun build(): ColumnMeta {
        return ColumnMeta(
            name = name,
            type = type,
            nullable = nullable,
            primaryKey = primaryKey,
            unique = unique,
            autoIncrement = autoIncrement,
            defaultValue = defaultValue,
            references = references
        )
    }
}
