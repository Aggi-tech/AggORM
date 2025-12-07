package com.aggitech.orm.migrations.meta

import com.aggitech.orm.migrations.dsl.ColumnType
import com.aggitech.orm.table.ColumnRef

/**
 * Representa uma coluna de forma type-safe e imutavel.
 * Gerado automaticamente apos execucao de migrations.
 *
 * Exemplo de uso:
 * ```kotlin
 * object UsersTable : TableMeta("users") {
 *     val ID = uuid("id").primaryKey()
 *     val NAME = varchar("name", 100).notNull()
 * }
 * ```
 */
data class ColumnMeta(
    override val name: String,
    val type: ColumnType,
    val nullable: Boolean = true,
    val primaryKey: Boolean = false,
    val unique: Boolean = false,
    val autoIncrement: Boolean = false,
    val defaultValue: String? = null,
    val references: ForeignKeyMeta? = null
) : ColumnRef {
    /**
     * Marca a coluna como NOT NULL
     */
    fun notNull(): ColumnMeta = copy(nullable = false)

    /**
     * Marca a coluna como NULLABLE
     */
    fun nullable(): ColumnMeta = copy(nullable = true)

    /**
     * Marca a coluna como PRIMARY KEY
     */
    fun primaryKey(): ColumnMeta = copy(primaryKey = true)

    /**
     * Marca a coluna como UNIQUE
     */
    fun unique(): ColumnMeta = copy(unique = true)

    /**
     * Marca a coluna como AUTO INCREMENT
     */
    fun autoIncrement(): ColumnMeta = copy(autoIncrement = true)

    /**
     * Define um valor default para a coluna
     */
    fun default(value: String): ColumnMeta = copy(defaultValue = value)

    /**
     * Define uma referencia de foreign key
     */
    fun references(table: String, column: String, onDelete: String? = null, onUpdate: String? = null): ColumnMeta =
        copy(references = ForeignKeyMeta(table, column, onDelete, onUpdate))

    /**
     * Define uma referencia de foreign key usando outro ColumnMeta
     */
    fun references(column: ColumnMeta, onDelete: String? = null, onUpdate: String? = null): ColumnMeta =
        copy(references = ForeignKeyMeta(column.tableName, column.name, onDelete, onUpdate))

    /**
     * Nome da tabela pai (preenchido pelo TableMeta)
     */
    @Transient
    private var _tableName: String = ""

    override val tableName: String
        get() = _tableName

    internal fun setTableName(name: String) {
        _tableName = name
    }
}

/**
 * Representa uma referencia de foreign key
 */
data class ForeignKeyMeta(
    val table: String,
    val column: String,
    val onDelete: String? = null,
    val onUpdate: String? = null
)
