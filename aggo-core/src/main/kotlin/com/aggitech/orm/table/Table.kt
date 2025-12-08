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
    // Note: These methods return ColumnRef to allow subclasses (like TableMeta)
    // to override with different column types (like ColumnMeta)

    protected open fun uuid(name: String): ColumnRef =
        Column(name, tableName, ColumnType.UUID)

    protected open fun varchar(name: String, length: Int = 255): ColumnRef =
        Column(name, tableName, ColumnType.VARCHAR)

    protected open fun char(name: String, length: Int): ColumnRef =
        Column(name, tableName, ColumnType.CHAR)

    protected open fun text(name: String): ColumnRef =
        Column(name, tableName, ColumnType.TEXT)

    protected open fun integer(name: String): ColumnRef =
        Column(name, tableName, ColumnType.INTEGER)

    protected open fun bigint(name: String): ColumnRef =
        Column(name, tableName, ColumnType.BIGINT)

    protected open fun smallint(name: String): ColumnRef =
        Column(name, tableName, ColumnType.SMALLINT)

    protected open fun boolean(name: String): ColumnRef =
        Column(name, tableName, ColumnType.BOOLEAN)

    protected open fun timestamp(name: String): ColumnRef =
        Column(name, tableName, ColumnType.TIMESTAMP)

    protected open fun timestamptz(name: String): ColumnRef =
        Column(name, tableName, ColumnType.TIMESTAMPTZ)

    protected open fun date(name: String): ColumnRef =
        Column(name, tableName, ColumnType.DATE)

    protected open fun time(name: String): ColumnRef =
        Column(name, tableName, ColumnType.TIME)

    protected open fun decimal(name: String, precision: Int = 10, scale: Int = 2): ColumnRef =
        Column(name, tableName, ColumnType.DECIMAL)

    protected open fun numeric(name: String, precision: Int = 10, scale: Int = 2): ColumnRef =
        Column(name, tableName, ColumnType.DECIMAL)

    protected open fun float(name: String): ColumnRef =
        Column(name, tableName, ColumnType.FLOAT)

    protected open fun double(name: String): ColumnRef =
        Column(name, tableName, ColumnType.DOUBLE)

    protected open fun binary(name: String, length: Int? = null): ColumnRef =
        Column(name, tableName, ColumnType.BINARY)

    protected open fun blob(name: String): ColumnRef =
        Column(name, tableName, ColumnType.BLOB)

    protected open fun json(name: String): ColumnRef =
        Column(name, tableName, ColumnType.JSON)

    protected open fun jsonb(name: String): ColumnRef =
        Column(name, tableName, ColumnType.JSONB)

    protected open fun serial(name: String): ColumnRef =
        Column(name, tableName, ColumnType.SERIAL)

    protected open fun bigserial(name: String): ColumnRef =
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
