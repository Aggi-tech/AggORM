package com.aggitech.orm.table

/**
 * Represents a table column for type-safe queries.
 * Automatically generated after running migrations.
 *
 * Usage:
 * ```kotlin
 * // Query using generated Mirror
 * val users = select(UserMirror) {
 *     UserMirror.STATUS eq "ACTIVE"
 * }.executeAs<User>()
 * ```
 */
data class Column(
    override val name: String,
    override val tableName: String,
    val type: ColumnType = ColumnType.VARCHAR,
    val nullable: Boolean = true,
    val primaryKey: Boolean = false,
    val unique: Boolean = false,
    val defaultValue: String? = null,
    val references: ForeignKeyRef? = null
) : ColumnRef {
    /**
     * Fully qualified column name (table.column)
     */
    val qualifiedName: String get() = "$tableName.$name"

    /**
     * Mark column as NOT NULL
     */
    fun notNull(): Column = copy(nullable = false)

    /**
     * Mark column as PRIMARY KEY
     */
    fun primaryKey(): Column = copy(primaryKey = true)

    /**
     * Mark column as UNIQUE
     */
    fun unique(): Column = copy(unique = true)

    /**
     * Set a default value
     */
    fun default(value: String): Column = copy(defaultValue = value)

    /**
     * Define a foreign key reference
     */
    fun references(table: String, column: String, onDelete: String? = null, onUpdate: String? = null): Column =
        copy(references = ForeignKeyRef(table, column, onDelete, onUpdate))

    override fun toString(): String = name
}

/**
 * Supported column types
 */
enum class ColumnType {
    UUID, VARCHAR, CHAR, TEXT,
    INTEGER, BIGINT, SMALLINT, SERIAL, BIGSERIAL,
    BOOLEAN,
    TIMESTAMP, TIMESTAMPTZ, DATE, TIME,
    DECIMAL, FLOAT, DOUBLE,
    BINARY, BLOB,
    JSON, JSONB
}

/**
 * Foreign key reference
 */
data class ForeignKeyRef(
    val table: String,
    val column: String,
    val onDelete: String? = null,
    val onUpdate: String? = null
)
