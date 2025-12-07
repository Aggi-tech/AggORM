package com.aggitech.orm.table

import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Base class for table metadata used in type-safe queries.
 *
 * Auto-generated Mirror classes extend this class to provide compile-time
 * safe column references for database operations.
 *
 * Example of auto-generated Mirror (do not edit manually):
 * ```kotlin
 * // File: generated/UserMirror.kt - AUTO-GENERATED, DO NOT EDIT
 * object UserMirror : Table("user", "public") {
 *     val ID = uuid("id").primaryKey().notNull()
 *     val NAME = varchar("name", 100).notNull()
 *     val EMAIL = varchar("email", 255).notNull().unique()
 *     val CREATED_AT = timestamp("created_at").notNull().default("CURRENT_TIMESTAMP")
 * }
 * ```
 *
 * Usage in queries:
 * ```kotlin
 * // Simple syntax
 * select(UserMirror) {
 *     UserMirror.NAME eq "John"
 * }.executeAs<User>()
 *
 * // Fluent syntax
 * from(UserMirror)
 *     .select(UserMirror.ID, UserMirror.NAME)
 *     .where { UserMirror.STATUS eq "ACTIVE" }
 *     .orderBy(UserMirror.NAME.asc())
 *     .executeAs<User>()
 * ```
 *
 * @see com.aggitech.orm.migrations.generator.MirrorGenerator
 */
abstract class Table(
    val tableName: String,
    val schema: String = "public"
) {
    /**
     * List of all columns in the table.
     * Automatically discovered via reflection.
     */
    val columns: List<Column> by lazy {
        this::class.memberProperties
            .filter { it.returnType.classifier == Column::class }
            .mapNotNull { prop ->
                @Suppress("UNCHECKED_CAST")
                (prop as KProperty1<Table, Column>).get(this)
            }
    }

    // ==================== Column type helpers ====================

    protected fun uuid(name: String): Column =
        Column(name, tableName, ColumnType.UUID)

    protected fun varchar(name: String, length: Int = 255): Column =
        Column(name, tableName, ColumnType.VARCHAR)

    protected fun char(name: String, length: Int): Column =
        Column(name, tableName, ColumnType.CHAR)

    protected fun text(name: String): Column =
        Column(name, tableName, ColumnType.TEXT)

    protected fun integer(name: String): Column =
        Column(name, tableName, ColumnType.INTEGER)

    protected fun bigint(name: String): Column =
        Column(name, tableName, ColumnType.BIGINT)

    protected fun smallint(name: String): Column =
        Column(name, tableName, ColumnType.SMALLINT)

    protected fun boolean(name: String): Column =
        Column(name, tableName, ColumnType.BOOLEAN)

    protected fun timestamp(name: String): Column =
        Column(name, tableName, ColumnType.TIMESTAMP)

    protected fun timestamptz(name: String): Column =
        Column(name, tableName, ColumnType.TIMESTAMPTZ)

    protected fun date(name: String): Column =
        Column(name, tableName, ColumnType.DATE)

    protected fun time(name: String): Column =
        Column(name, tableName, ColumnType.TIME)

    protected fun decimal(name: String, precision: Int = 10, scale: Int = 2): Column =
        Column(name, tableName, ColumnType.DECIMAL)

    protected fun numeric(name: String, precision: Int = 10, scale: Int = 2): Column =
        Column(name, tableName, ColumnType.DECIMAL)

    protected fun float(name: String): Column =
        Column(name, tableName, ColumnType.FLOAT)

    protected fun double(name: String): Column =
        Column(name, tableName, ColumnType.DOUBLE)

    protected fun binary(name: String, length: Int? = null): Column =
        Column(name, tableName, ColumnType.BINARY)

    protected fun blob(name: String): Column =
        Column(name, tableName, ColumnType.BLOB)

    protected fun json(name: String): Column =
        Column(name, tableName, ColumnType.JSON)

    protected fun jsonb(name: String): Column =
        Column(name, tableName, ColumnType.JSONB)

    protected fun serial(name: String): Column =
        Column(name, tableName, ColumnType.SERIAL)

    protected fun bigserial(name: String): Column =
        Column(name, tableName, ColumnType.BIGSERIAL)

    /**
     * Find a column by name
     */
    fun column(name: String): Column? =
        columns.find { it.name == name }

    /**
     * Get the primary key column
     */
    fun primaryKey(): Column? =
        columns.find { it.primaryKey }

    override fun toString(): String = tableName
}
