package com.aggitech.orm.table

import com.aggitech.orm.jdbc.JdbcConnectionManager
import com.aggitech.orm.mapping.EntityMapper
import com.aggitech.orm.security.SqlSecurity
import com.aggitech.orm.sql.context.RenderedSql
import kotlin.reflect.KClass

/**
 * Interface for any column-like object that can be used in queries.
 * Implemented by both Column (core) and ColumnMeta (migrations).
 */
interface ColumnRef {
    val name: String
    val tableName: String
}

// ==================== Security Helpers ====================

/**
 * Safely quotes an identifier after validation.
 * Uses double quotes (SQL standard) for identifiers.
 */
private fun safeQuote(identifier: String, type: String = "identifier"): String {
    SqlSecurity.validateIdentifier(identifier, type)
    return "\"$identifier\""
}

/**
 * Safely quotes a column reference (table.column format).
 */
private fun ColumnRef.safeRef(): String {
    SqlSecurity.validateIdentifier(tableName, "table name")
    SqlSecurity.validateIdentifier(name, "column name")
    return "\"$tableName\".\"$name\""
}

/**
 * Validates and quotes an alias.
 */
private fun safeAlias(alias: String): String {
    SqlSecurity.validateAlias(alias)
    return "\"$alias\""
}

/**
 * Type-safe DSL for queries using auto-generated Mirror and Column metadata.
 *
 * Mirror classes are auto-generated metadata representations of database tables
 * that provide compile-time type safety for queries.
 *
 * Usage examples:
 * ```kotlin
 * // SELECT with WHERE
 * val users: List<User> = select<UserMirror> {
 *     UserMirror.NAME eq "John"
 * }.executeAs<User>()
 *
 * // SELECT with multiple conditions
 * val activeUsers = select<UserMirror> {
 *     (UserMirror.STATUS eq "ACTIVE") and (UserMirror.AGE gte 18)
 * }.executeAs<User>()
 *
 * // SELECT all columns
 * val allUsers = select<UserMirror>().executeAs<User>()
 *
 * // INSERT
 * insert<UserMirror> {
 *     UserMirror.NAME to "John"
 *     UserMirror.EMAIL to "john@example.com"
 * }.execute()
 *
 * // UPDATE with values and where in same block
 * update<UserMirror> {
 *     UserMirror.FIRST_NAME to user.firstName
 *     UserMirror.LAST_NAME to user.lastName
 *     UserMirror.EMAIL to user.email
 *     UserMirror.UPDATED_AT to now
 *     where {
 *         UserMirror.ID eq user.id
 *     }
 * }.execute()
 *
 * // DELETE
 * delete<UserMirror> {
 *     UserMirror.ID eq userId
 * }.execute()
 * ```
 */

// ==================== Select Fields ====================

/**
 * Represents a field in SELECT clause
 */
sealed class SelectExpression {
    /** Single column: "table"."column" */
    data class Col(val column: ColumnRef) : SelectExpression()

    /** Column with alias: "table"."column" AS "alias" */
    data class ColAlias(val column: ColumnRef, val alias: String) : SelectExpression()

    /** All columns: * */
    object All : SelectExpression()

    /** Aggregate function: COUNT, SUM, AVG, MIN, MAX */
    data class Aggregate(
        val function: AggregateFunction,
        val column: ColumnRef?,
        val alias: String
    ) : SelectExpression()

    /** Raw SQL expression */
    data class Raw(val sql: String, val alias: String? = null) : SelectExpression()
}

enum class AggregateFunction {
    COUNT, COUNT_DISTINCT, SUM, AVG, MIN, MAX
}

// ==================== JOIN ====================

enum class JoinType { INNER, LEFT, RIGHT, FULL }

data class TableJoin(
    val type: JoinType,
    val table: Table,
    val condition: TablePredicate
)

// ==================== Predicates ====================

sealed class TablePredicate {
    data class Comparison(val column: ColumnRef, val operator: String, val value: Any?) : TablePredicate()
    data class ColumnComparison(val left: ColumnRef, val operator: String, val right: ColumnRef) : TablePredicate()
    data class In(val column: ColumnRef, val values: List<Any?>) : TablePredicate()
    data class NotIn(val column: ColumnRef, val values: List<Any?>) : TablePredicate()
    data class IsNull(val column: ColumnRef) : TablePredicate()
    data class IsNotNull(val column: ColumnRef) : TablePredicate()
    data class Like(val column: ColumnRef, val pattern: String) : TablePredicate()
    data class Between(val column: ColumnRef, val lower: Any, val upper: Any) : TablePredicate()
    data class And(val left: TablePredicate, val right: TablePredicate) : TablePredicate()
    data class Or(val left: TablePredicate, val right: TablePredicate) : TablePredicate()
    data class Not(val predicate: TablePredicate) : TablePredicate()
}

// ==================== Order ====================

data class ColumnOrder(val column: ColumnRef, val direction: OrderDirection)

enum class OrderDirection { ASC, DESC }

fun ColumnRef.asc(): ColumnOrder = ColumnOrder(this, OrderDirection.ASC)
fun ColumnRef.desc(): ColumnOrder = ColumnOrder(this, OrderDirection.DESC)

// ==================== Where Builder ====================

class TableWhereBuilder {
    // Comparison with values
    infix fun ColumnRef.eq(value: Any?): TablePredicate = TablePredicate.Comparison(this, "=", value)
    infix fun ColumnRef.ne(value: Any?): TablePredicate = TablePredicate.Comparison(this, "!=", value)
    infix fun ColumnRef.gt(value: Any): TablePredicate = TablePredicate.Comparison(this, ">", value)
    infix fun ColumnRef.gte(value: Any): TablePredicate = TablePredicate.Comparison(this, ">=", value)
    infix fun ColumnRef.lt(value: Any): TablePredicate = TablePredicate.Comparison(this, "<", value)
    infix fun ColumnRef.lte(value: Any): TablePredicate = TablePredicate.Comparison(this, "<=", value)
    infix fun ColumnRef.like(pattern: String): TablePredicate = TablePredicate.Like(this, pattern)
    infix fun ColumnRef.inList(values: List<Any?>): TablePredicate = TablePredicate.In(this, values)
    infix fun ColumnRef.notInList(values: List<Any?>): TablePredicate = TablePredicate.NotIn(this, values)

    // Comparison between columns (for JOINs)
    infix fun ColumnRef.eqCol(other: ColumnRef): TablePredicate = TablePredicate.ColumnComparison(this, "=", other)
    infix fun ColumnRef.neCol(other: ColumnRef): TablePredicate = TablePredicate.ColumnComparison(this, "!=", other)
    infix fun ColumnRef.gtCol(other: ColumnRef): TablePredicate = TablePredicate.ColumnComparison(this, ">", other)
    infix fun ColumnRef.gteCol(other: ColumnRef): TablePredicate = TablePredicate.ColumnComparison(this, ">=", other)
    infix fun ColumnRef.ltCol(other: ColumnRef): TablePredicate = TablePredicate.ColumnComparison(this, "<", other)
    infix fun ColumnRef.lteCol(other: ColumnRef): TablePredicate = TablePredicate.ColumnComparison(this, "<=", other)

    fun ColumnRef.isNull(): TablePredicate = TablePredicate.IsNull(this)
    fun ColumnRef.isNotNull(): TablePredicate = TablePredicate.IsNotNull(this)
    fun ColumnRef.between(lower: Any, upper: Any): TablePredicate = TablePredicate.Between(this, lower, upper)

    infix fun TablePredicate.and(other: TablePredicate): TablePredicate = TablePredicate.And(this, other)
    infix fun TablePredicate.or(other: TablePredicate): TablePredicate = TablePredicate.Or(this, other)
    fun not(predicate: TablePredicate): TablePredicate = TablePredicate.Not(predicate)
}

// ==================== Select Builder (for DSL block syntax) ====================

class TableSelectFieldBuilder {
    internal val fields = mutableListOf<SelectExpression>()

    /** Add a column to SELECT */
    operator fun ColumnRef.unaryPlus() {
        fields.add(SelectExpression.Col(this))
    }

    /** Add a column with alias */
    infix fun ColumnRef.alias(name: String) {
        fields.add(SelectExpression.ColAlias(this, name))
    }

    /** COUNT(*) */
    fun countAll(alias: String) {
        fields.add(SelectExpression.Aggregate(AggregateFunction.COUNT, null, alias))
    }

    /** COUNT(column) */
    fun count(column: ColumnRef, alias: String) {
        fields.add(SelectExpression.Aggregate(AggregateFunction.COUNT, column, alias))
    }

    /** COUNT(DISTINCT column) */
    fun countDistinct(column: ColumnRef, alias: String) {
        fields.add(SelectExpression.Aggregate(AggregateFunction.COUNT_DISTINCT, column, alias))
    }

    /** SUM(column) */
    fun sum(column: ColumnRef, alias: String) {
        fields.add(SelectExpression.Aggregate(AggregateFunction.SUM, column, alias))
    }

    /** AVG(column) */
    fun avg(column: ColumnRef, alias: String) {
        fields.add(SelectExpression.Aggregate(AggregateFunction.AVG, column, alias))
    }

    /** MIN(column) */
    fun min(column: ColumnRef, alias: String) {
        fields.add(SelectExpression.Aggregate(AggregateFunction.MIN, column, alias))
    }

    /** MAX(column) */
    fun max(column: ColumnRef, alias: String) {
        fields.add(SelectExpression.Aggregate(AggregateFunction.MAX, column, alias))
    }

    /** Raw SQL expression */
    fun raw(sql: String, alias: String? = null) {
        fields.add(SelectExpression.Raw(sql, alias))
    }
}

// ==================== SELECT Query Builder ====================

class TableSelectBuilder(private val table: Table) {
    private val selectFields = mutableListOf<SelectExpression>()
    private val joins = mutableListOf<TableJoin>()
    private var predicate: TablePredicate? = null
    private val groupByColumns = mutableListOf<ColumnRef>()
    private var havingPredicate: TablePredicate? = null
    private val orderByList = mutableListOf<ColumnOrder>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null
    private var distinctFlag = false

    /** Select specific columns */
    fun select(vararg cols: ColumnRef): TableSelectBuilder {
        cols.forEach { selectFields.add(SelectExpression.Col(it)) }
        return this
    }

    /** Select using DSL block with aggregations */
    fun select(block: TableSelectFieldBuilder.() -> Unit): TableSelectBuilder {
        val builder = TableSelectFieldBuilder()
        builder.block()
        selectFields.addAll(builder.fields)
        return this
    }

    /** Select all columns */
    fun selectAll(): TableSelectBuilder {
        selectFields.clear()
        selectFields.add(SelectExpression.All)
        return this
    }

    // ==================== JOINs ====================

    fun innerJoin(joinTable: Table, block: TableWhereBuilder.() -> TablePredicate): TableSelectBuilder {
        joins.add(TableJoin(JoinType.INNER, joinTable, TableWhereBuilder().block()))
        return this
    }

    fun leftJoin(joinTable: Table, block: TableWhereBuilder.() -> TablePredicate): TableSelectBuilder {
        joins.add(TableJoin(JoinType.LEFT, joinTable, TableWhereBuilder().block()))
        return this
    }

    fun rightJoin(joinTable: Table, block: TableWhereBuilder.() -> TablePredicate): TableSelectBuilder {
        joins.add(TableJoin(JoinType.RIGHT, joinTable, TableWhereBuilder().block()))
        return this
    }

    fun fullJoin(joinTable: Table, block: TableWhereBuilder.() -> TablePredicate): TableSelectBuilder {
        joins.add(TableJoin(JoinType.FULL, joinTable, TableWhereBuilder().block()))
        return this
    }

    // ==================== WHERE / GROUP BY / HAVING / ORDER BY ====================

    fun where(block: TableWhereBuilder.() -> TablePredicate): TableSelectBuilder {
        predicate = TableWhereBuilder().block()
        return this
    }

    fun groupBy(vararg cols: ColumnRef): TableSelectBuilder {
        groupByColumns.addAll(cols)
        return this
    }

    fun having(block: TableWhereBuilder.() -> TablePredicate): TableSelectBuilder {
        havingPredicate = TableWhereBuilder().block()
        return this
    }

    fun orderBy(vararg orders: ColumnOrder): TableSelectBuilder {
        orderByList.addAll(orders)
        return this
    }

    fun limit(value: Int): TableSelectBuilder {
        limitValue = value
        return this
    }

    fun offset(value: Int): TableSelectBuilder {
        offsetValue = value
        return this
    }

    fun distinct(): TableSelectBuilder {
        distinctFlag = true
        return this
    }

    // ==================== Build ====================

    fun build(): RenderedSql {
        val sql = StringBuilder()
        val params = mutableListOf<Any?>()

        // SELECT
        sql.append("SELECT ")
        if (distinctFlag) sql.append("DISTINCT ")

        if (selectFields.isEmpty()) {
            sql.append("*")
        } else {
            sql.append(selectFields.joinToString(", ") { renderSelectExpression(it) })
        }

        // FROM - validate table name
        sql.append(" FROM ${safeQuote(table.tableName, "table name")}")

        // JOINs
        for (join in joins) {
            val joinKeyword = when (join.type) {
                JoinType.INNER -> "INNER JOIN"
                JoinType.LEFT -> "LEFT JOIN"
                JoinType.RIGHT -> "RIGHT JOIN"
                JoinType.FULL -> "FULL OUTER JOIN"
            }
            sql.append(" $joinKeyword ${safeQuote(join.table.tableName, "join table name")} ON ")
            appendPredicate(sql, params, join.condition)
        }

        // WHERE
        if (predicate != null) {
            sql.append(" WHERE ")
            appendPredicate(sql, params, predicate!!)
        }

        // GROUP BY - validate column references
        if (groupByColumns.isNotEmpty()) {
            sql.append(" GROUP BY ")
            sql.append(groupByColumns.joinToString(", ") { it.safeRef() })
        }

        // HAVING
        if (havingPredicate != null) {
            sql.append(" HAVING ")
            appendPredicate(sql, params, havingPredicate!!)
        }

        // ORDER BY - validate column references
        if (orderByList.isNotEmpty()) {
            sql.append(" ORDER BY ")
            sql.append(orderByList.joinToString(", ") {
                "${it.column.safeRef()} ${it.direction.name}"
            })
        }

        // LIMIT - validated as integer (no injection risk)
        if (limitValue != null) {
            sql.append(" LIMIT $limitValue")
        }

        // OFFSET - validated as integer (no injection risk)
        if (offsetValue != null) {
            sql.append(" OFFSET $offsetValue")
        }

        return RenderedSql(sql.toString(), params)
    }

    private fun renderSelectExpression(expr: SelectExpression): String {
        return when (expr) {
            is SelectExpression.Col -> expr.column.safeRef()
            is SelectExpression.ColAlias -> "${expr.column.safeRef()} AS ${safeAlias(expr.alias)}"
            is SelectExpression.All -> "*"
            is SelectExpression.Aggregate -> {
                val funcName = when (expr.function) {
                    AggregateFunction.COUNT -> "COUNT"
                    AggregateFunction.COUNT_DISTINCT -> "COUNT(DISTINCT"
                    AggregateFunction.SUM -> "SUM"
                    AggregateFunction.AVG -> "AVG"
                    AggregateFunction.MIN -> "MIN"
                    AggregateFunction.MAX -> "MAX"
                }
                val colRef = if (expr.column == null) "*" else expr.column.safeRef()
                val aliasQuoted = safeAlias(expr.alias)
                if (expr.function == AggregateFunction.COUNT_DISTINCT) {
                    "$funcName $colRef) AS $aliasQuoted"
                } else {
                    "$funcName($colRef) AS $aliasQuoted"
                }
            }
            is SelectExpression.Raw -> {
                // Validate raw SQL for dangerous patterns
                SqlSecurity.validateRawSql(expr.sql)
                if (expr.alias != null) "${expr.sql} AS ${safeAlias(expr.alias)}" else expr.sql
            }
        }
    }

    // ==================== Execute ====================

    /** Execute query and return raw results */
    fun execute(): List<Map<String, Any?>> {
        val rendered = build()
        return JdbcConnectionManager.withConnection { conn ->
            conn.prepareStatement(rendered.sql).use { stmt ->
                rendered.parameters.forEachIndexed { index, value ->
                    stmt.setObject(index + 1, value)
                }
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<Map<String, Any?>>()
                    val metaData = rs.metaData
                    val columnCount = metaData.columnCount
                    while (rs.next()) {
                        val row = mutableMapOf<String, Any?>()
                        for (i in 1..columnCount) {
                            val colName = metaData.getColumnLabel(i) ?: metaData.getColumnName(i)
                            row[colName] = rs.getObject(i)
                        }
                        results.add(row)
                    }
                    results
                }
            }
        }
    }

    /** Execute query and map to entities */
    inline fun <reified T : Any> executeAs(): List<T> {
        return execute().map { EntityMapper.map(it, T::class) }
    }

    /**
     * Execute query and map to DTOs/Projections.
     *
     * Supports both classes (data classes) and interfaces.
     *
     * Usage with data class:
     * ```kotlin
     * data class UserSummary(val id: UUID, val name: String)
     *
     * val summaries = select(UsersTable)
     *     .select(UsersTable.ID, UsersTable.NAME)
     *     .executeAsProjection<UserSummary>()
     * ```
     *
     * Usage with interface:
     * ```kotlin
     * interface UserNameOnly {
     *     val name: String
     * }
     *
     * val names = select(UsersTable)
     *     .select(UsersTable.NAME)
     *     .executeAsProjection<UserNameOnly>()
     * ```
     */
    inline fun <reified T : Any> executeAsProjection(): List<T> {
        return execute().map { EntityMapper.map(it, T::class) }
    }

    /**
     * Execute query and map to DTO/Projection using explicit class.
     *
     * Usage:
     * ```kotlin
     * val summaries = select(UsersTable)
     *     .select(UsersTable.ID, UsersTable.NAME)
     *     .executeAsProjection(UserSummary::class)
     * ```
     */
    fun <T : Any> executeAsProjection(projectionClass: KClass<T>): List<T> {
        return execute().map { EntityMapper.map(it, projectionClass) }
    }

    /** Execute query and return single result or null */
    fun executeOne(): Map<String, Any?>? {
        return limit(1).execute().firstOrNull()
    }

    /** Execute query and map single result to entity or null */
    inline fun <reified T : Any> executeOneAs(): T? {
        return executeOne()?.let { EntityMapper.map(it, T::class) }
    }

    /**
     * Execute query and map single result to DTO/Projection or null.
     */
    inline fun <reified T : Any> executeOneAsProjection(): T? {
        return executeOne()?.let { EntityMapper.map(it, T::class) }
    }

    /**
     * Execute query and map single result to DTO/Projection or null using explicit class.
     */
    fun <T : Any> executeOneAsProjection(projectionClass: KClass<T>): T? {
        return executeOne()?.let { EntityMapper.map(it, projectionClass) }
    }

    private fun appendPredicate(sql: StringBuilder, params: MutableList<Any?>, pred: TablePredicate) {
        when (pred) {
            is TablePredicate.Comparison -> {
                sql.append("${pred.column.safeRef()} ${pred.operator} ?")
                SqlSecurity.validateValue(pred.value)
                params.add(pred.value)
            }
            is TablePredicate.ColumnComparison -> {
                sql.append("${pred.left.safeRef()} ${pred.operator} ${pred.right.safeRef()}")
            }
            is TablePredicate.In -> {
                sql.append("${pred.column.safeRef()} IN (${pred.values.joinToString(",") { "?" }})")
                pred.values.forEach { SqlSecurity.validateValue(it) }
                params.addAll(pred.values)
            }
            is TablePredicate.NotIn -> {
                sql.append("${pred.column.safeRef()} NOT IN (${pred.values.joinToString(",") { "?" }})")
                pred.values.forEach { SqlSecurity.validateValue(it) }
                params.addAll(pred.values)
            }
            is TablePredicate.IsNull -> {
                sql.append("${pred.column.safeRef()} IS NULL")
            }
            is TablePredicate.IsNotNull -> {
                sql.append("${pred.column.safeRef()} IS NOT NULL")
            }
            is TablePredicate.Like -> {
                sql.append("${pred.column.safeRef()} LIKE ?")
                SqlSecurity.validateLikePattern(pred.pattern)
                params.add(pred.pattern)
            }
            is TablePredicate.Between -> {
                sql.append("${pred.column.safeRef()} BETWEEN ? AND ?")
                SqlSecurity.validateValue(pred.lower)
                SqlSecurity.validateValue(pred.upper)
                params.add(pred.lower)
                params.add(pred.upper)
            }
            is TablePredicate.And -> {
                sql.append("(")
                appendPredicate(sql, params, pred.left)
                sql.append(" AND ")
                appendPredicate(sql, params, pred.right)
                sql.append(")")
            }
            is TablePredicate.Or -> {
                sql.append("(")
                appendPredicate(sql, params, pred.left)
                sql.append(" OR ")
                appendPredicate(sql, params, pred.right)
                sql.append(")")
            }
            is TablePredicate.Not -> {
                sql.append("NOT (")
                appendPredicate(sql, params, pred.predicate)
                sql.append(")")
            }
        }
    }
}

// ==================== INSERT Query Builder ====================

class TableInsertBuilder(private val table: Table) {
    private val columnValues = mutableMapOf<ColumnRef, Any?>()
    private var returningColumns = mutableListOf<ColumnRef>()

    fun set(column: ColumnRef, value: Any?): TableInsertBuilder {
        columnValues[column] = value
        return this
    }

    infix fun ColumnRef.to(value: Any?) {
        columnValues[this] = value
    }

    fun values(block: TableInsertBuilder.() -> Unit): TableInsertBuilder {
        this.block()
        return this
    }

    /** RETURNING clause (PostgreSQL) */
    fun returning(vararg cols: ColumnRef): TableInsertBuilder {
        returningColumns.addAll(cols)
        return this
    }

    fun build(): RenderedSql {
        val columns = columnValues.keys.toList()
        val sql = StringBuilder()
        val params = mutableListOf<Any?>()

        sql.append("INSERT INTO ${safeQuote(table.tableName, "table name")} (")
        sql.append(columns.joinToString(", ") { safeQuote(it.name, "column name") })
        sql.append(") VALUES (")
        sql.append(columns.joinToString(", ") { "?" })
        sql.append(")")

        // Validate values before adding to params
        columnValues.values.forEach { SqlSecurity.validateValue(it) }
        params.addAll(columnValues.values)

        if (returningColumns.isNotEmpty()) {
            sql.append(" RETURNING ")
            sql.append(returningColumns.joinToString(", ") { safeQuote(it.name, "column name") })
        }

        return RenderedSql(sql.toString(), params)
    }

    /** Execute insert and return affected rows count */
    fun execute(): Int {
        val rendered = build()
        return JdbcConnectionManager.withConnection { conn ->
            conn.prepareStatement(rendered.sql).use { stmt ->
                rendered.parameters.forEachIndexed { index, value ->
                    stmt.setObject(index + 1, value)
                }
                stmt.executeUpdate()
            }
        }
    }

    /** Execute insert with RETURNING and map to entity */
    inline fun <reified T : Any> executeReturning(): T? {
        val rendered = build()
        return JdbcConnectionManager.withConnection { conn ->
            conn.prepareStatement(rendered.sql).use { stmt ->
                rendered.parameters.forEachIndexed { index, value ->
                    stmt.setObject(index + 1, value)
                }
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val row = mutableMapOf<String, Any?>()
                        val metaData = rs.metaData
                        for (i in 1..metaData.columnCount) {
                            val colName = metaData.getColumnLabel(i) ?: metaData.getColumnName(i)
                            row[colName] = rs.getObject(i)
                        }
                        EntityMapper.map(row, T::class)
                    } else null
                }
            }
        }
    }

    /** Execute insert and return generated keys */
    fun executeReturningKeys(): List<Any> {
        val rendered = build()
        return JdbcConnectionManager.withConnection { conn ->
            conn.prepareStatement(rendered.sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                rendered.parameters.forEachIndexed { index, value ->
                    stmt.setObject(index + 1, value)
                }
                stmt.executeUpdate()
                val keys = mutableListOf<Any>()
                stmt.generatedKeys.use { rs ->
                    while (rs.next()) {
                        keys.add(rs.getObject(1))
                    }
                }
                keys
            }
        }
    }
}

// ==================== UPDATE Query Builder ====================

class TableUpdateBuilder(private val table: Table) {
    private val updates = mutableMapOf<ColumnRef, Any?>()
    private var predicate: TablePredicate? = null
    private var returningColumns = mutableListOf<ColumnRef>()

    fun set(column: ColumnRef, value: Any?): TableUpdateBuilder {
        updates[column] = value
        return this
    }

    infix fun ColumnRef.to(value: Any?) {
        updates[this] = value
    }

    fun set(block: TableUpdateBuilder.() -> Unit): TableUpdateBuilder {
        this.block()
        return this
    }

    fun where(block: TableWhereBuilder.() -> TablePredicate): TableUpdateBuilder {
        predicate = TableWhereBuilder().block()
        return this
    }

    /** RETURNING clause (PostgreSQL) */
    fun returning(vararg cols: ColumnRef): TableUpdateBuilder {
        returningColumns.addAll(cols)
        return this
    }

    fun build(): RenderedSql {
        val sql = StringBuilder()
        val params = mutableListOf<Any?>()

        sql.append("UPDATE ${safeQuote(table.tableName, "table name")} SET ")
        sql.append(updates.entries.joinToString(", ") { "${safeQuote(it.key.name, "column name")} = ?" })

        // Validate values before adding to params
        updates.values.forEach { SqlSecurity.validateValue(it) }
        params.addAll(updates.values)

        if (predicate != null) {
            sql.append(" WHERE ")
            appendPredicate(sql, params, predicate!!)
        }

        if (returningColumns.isNotEmpty()) {
            sql.append(" RETURNING ")
            sql.append(returningColumns.joinToString(", ") { safeQuote(it.name, "column name") })
        }

        return RenderedSql(sql.toString(), params)
    }

    /** Execute update and return affected rows count */
    fun execute(): Int {
        val rendered = build()
        return JdbcConnectionManager.withConnection { conn ->
            conn.prepareStatement(rendered.sql).use { stmt ->
                rendered.parameters.forEachIndexed { index, value ->
                    stmt.setObject(index + 1, value)
                }
                stmt.executeUpdate()
            }
        }
    }

    /** Execute update with RETURNING and map to entities */
    inline fun <reified T : Any> executeReturning(): List<T> {
        val rendered = build()
        return JdbcConnectionManager.withConnection { conn ->
            conn.prepareStatement(rendered.sql).use { stmt ->
                rendered.parameters.forEachIndexed { index, value ->
                    stmt.setObject(index + 1, value)
                }
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<T>()
                    val metaData = rs.metaData
                    while (rs.next()) {
                        val row = mutableMapOf<String, Any?>()
                        for (i in 1..metaData.columnCount) {
                            val colName = metaData.getColumnLabel(i) ?: metaData.getColumnName(i)
                            row[colName] = rs.getObject(i)
                        }
                        results.add(EntityMapper.map(row, T::class))
                    }
                    results
                }
            }
        }
    }

    private fun appendPredicate(sql: StringBuilder, params: MutableList<Any?>, pred: TablePredicate) {
        when (pred) {
            is TablePredicate.Comparison -> {
                sql.append("${pred.column.safeRef()} ${pred.operator} ?")
                SqlSecurity.validateValue(pred.value)
                params.add(pred.value)
            }
            is TablePredicate.ColumnComparison -> {
                sql.append("${pred.left.safeRef()} ${pred.operator} ${pred.right.safeRef()}")
            }
            is TablePredicate.In -> {
                sql.append("${pred.column.safeRef()} IN (${pred.values.joinToString(",") { "?" }})")
                pred.values.forEach { SqlSecurity.validateValue(it) }
                params.addAll(pred.values)
            }
            is TablePredicate.NotIn -> {
                sql.append("${pred.column.safeRef()} NOT IN (${pred.values.joinToString(",") { "?" }})")
                pred.values.forEach { SqlSecurity.validateValue(it) }
                params.addAll(pred.values)
            }
            is TablePredicate.IsNull -> sql.append("${pred.column.safeRef()} IS NULL")
            is TablePredicate.IsNotNull -> sql.append("${pred.column.safeRef()} IS NOT NULL")
            is TablePredicate.Like -> {
                sql.append("${pred.column.safeRef()} LIKE ?")
                SqlSecurity.validateLikePattern(pred.pattern)
                params.add(pred.pattern)
            }
            is TablePredicate.Between -> {
                sql.append("${pred.column.safeRef()} BETWEEN ? AND ?")
                SqlSecurity.validateValue(pred.lower)
                SqlSecurity.validateValue(pred.upper)
                params.add(pred.lower)
                params.add(pred.upper)
            }
            is TablePredicate.And -> {
                sql.append("(")
                appendPredicate(sql, params, pred.left)
                sql.append(" AND ")
                appendPredicate(sql, params, pred.right)
                sql.append(")")
            }
            is TablePredicate.Or -> {
                sql.append("(")
                appendPredicate(sql, params, pred.left)
                sql.append(" OR ")
                appendPredicate(sql, params, pred.right)
                sql.append(")")
            }
            is TablePredicate.Not -> {
                sql.append("NOT (")
                appendPredicate(sql, params, pred.predicate)
                sql.append(")")
            }
        }
    }
}

// ==================== DELETE Query Builder ====================

class TableDeleteBuilder(private val table: Table) {
    private var predicate: TablePredicate? = null
    private var returningColumns = mutableListOf<ColumnRef>()

    fun where(block: TableWhereBuilder.() -> TablePredicate): TableDeleteBuilder {
        predicate = TableWhereBuilder().block()
        return this
    }

    /** RETURNING clause (PostgreSQL) */
    fun returning(vararg cols: ColumnRef): TableDeleteBuilder {
        returningColumns.addAll(cols)
        return this
    }

    fun build(): RenderedSql {
        val sql = StringBuilder()
        val params = mutableListOf<Any?>()

        sql.append("DELETE FROM ${safeQuote(table.tableName, "table name")}")

        if (predicate != null) {
            sql.append(" WHERE ")
            appendPredicate(sql, params, predicate!!)
        }

        if (returningColumns.isNotEmpty()) {
            sql.append(" RETURNING ")
            sql.append(returningColumns.joinToString(", ") { safeQuote(it.name, "column name") })
        }

        return RenderedSql(sql.toString(), params)
    }

    /** Execute delete and return affected rows count */
    fun execute(): Int {
        val rendered = build()
        return JdbcConnectionManager.withConnection { conn ->
            conn.prepareStatement(rendered.sql).use { stmt ->
                rendered.parameters.forEachIndexed { index, value ->
                    stmt.setObject(index + 1, value)
                }
                stmt.executeUpdate()
            }
        }
    }

    /** Execute delete with RETURNING and map to entities */
    inline fun <reified T : Any> executeReturning(): List<T> {
        val rendered = build()
        return JdbcConnectionManager.withConnection { conn ->
            conn.prepareStatement(rendered.sql).use { stmt ->
                rendered.parameters.forEachIndexed { index, value ->
                    stmt.setObject(index + 1, value)
                }
                stmt.executeQuery().use { rs ->
                    val results = mutableListOf<T>()
                    val metaData = rs.metaData
                    while (rs.next()) {
                        val row = mutableMapOf<String, Any?>()
                        for (i in 1..metaData.columnCount) {
                            val colName = metaData.getColumnLabel(i) ?: metaData.getColumnName(i)
                            row[colName] = rs.getObject(i)
                        }
                        results.add(EntityMapper.map(row, T::class))
                    }
                    results
                }
            }
        }
    }

    private fun appendPredicate(sql: StringBuilder, params: MutableList<Any?>, pred: TablePredicate) {
        when (pred) {
            is TablePredicate.Comparison -> {
                sql.append("${pred.column.safeRef()} ${pred.operator} ?")
                SqlSecurity.validateValue(pred.value)
                params.add(pred.value)
            }
            is TablePredicate.ColumnComparison -> {
                sql.append("${pred.left.safeRef()} ${pred.operator} ${pred.right.safeRef()}")
            }
            is TablePredicate.In -> {
                sql.append("${pred.column.safeRef()} IN (${pred.values.joinToString(",") { "?" }})")
                pred.values.forEach { SqlSecurity.validateValue(it) }
                params.addAll(pred.values)
            }
            is TablePredicate.NotIn -> {
                sql.append("${pred.column.safeRef()} NOT IN (${pred.values.joinToString(",") { "?" }})")
                pred.values.forEach { SqlSecurity.validateValue(it) }
                params.addAll(pred.values)
            }
            is TablePredicate.IsNull -> sql.append("${pred.column.safeRef()} IS NULL")
            is TablePredicate.IsNotNull -> sql.append("${pred.column.safeRef()} IS NOT NULL")
            is TablePredicate.Like -> {
                sql.append("${pred.column.safeRef()} LIKE ?")
                SqlSecurity.validateLikePattern(pred.pattern)
                params.add(pred.pattern)
            }
            is TablePredicate.Between -> {
                sql.append("${pred.column.safeRef()} BETWEEN ? AND ?")
                SqlSecurity.validateValue(pred.lower)
                SqlSecurity.validateValue(pred.upper)
                params.add(pred.lower)
                params.add(pred.upper)
            }
            is TablePredicate.And -> {
                sql.append("(")
                appendPredicate(sql, params, pred.left)
                sql.append(" AND ")
                appendPredicate(sql, params, pred.right)
                sql.append(")")
            }
            is TablePredicate.Or -> {
                sql.append("(")
                appendPredicate(sql, params, pred.left)
                sql.append(" OR ")
                appendPredicate(sql, params, pred.right)
                sql.append(")")
            }
            is TablePredicate.Not -> {
                sql.append("NOT (")
                appendPredicate(sql, params, pred.predicate)
                sql.append(")")
            }
        }
    }
}

// ==================== Entry Point Functions (Simplified Syntax with Generics) ====================

/**
 * SELECT with optional WHERE condition directly in block using reified generics
 *
 * ```kotlin
 * // Simple SELECT with WHERE
 * val users = select<UserMirror> {
 *     UserMirror.NAME eq "John"
 * }.executeAs<User>()
 *
 * // SELECT with multiple conditions
 * val results = select<UserMirror> {
 *     (UserMirror.STATUS eq "ACTIVE") and (UserMirror.AGE gte 18)
 * }.executeAs<User>()
 * ```
 */
inline fun <reified T : Table> select(noinline block: TableWhereBuilder.() -> TablePredicate): TableSelectBuilder {
    val table = T::class.objectInstance
        ?: throw IllegalArgumentException("${T::class.simpleName} must be an object (singleton)")
    return TableSelectBuilder(table).where(block)
}

/**
 * SELECT all from table (no WHERE) using reified generics
 *
 * ```kotlin
 * val allUsers = select<UserMirror>().executeAs<User>()
 * ```
 */
inline fun <reified T : Table> select(): TableSelectBuilder {
    val table = T::class.objectInstance
        ?: throw IllegalArgumentException("${T::class.simpleName} must be an object (singleton)")
    return TableSelectBuilder(table)
}

/**
 * INSERT with values directly in block using reified generics
 *
 * ```kotlin
 * insert<UserMirror> {
 *     UserMirror.NAME to "John"
 *     UserMirror.EMAIL to "john@example.com"
 * }.execute()
 * ```
 */
inline fun <reified T : Table> insert(noinline block: TableInsertBuilder.() -> Unit): TableInsertBuilder {
    val table = T::class.objectInstance
        ?: throw IllegalArgumentException("${T::class.simpleName} must be an object (singleton)")
    return TableInsertBuilder(table).values(block)
}

/**
 * UPDATE with values and where in a combined builder using reified generics
 *
 * ```kotlin
 * update<UserMirror> {
 *     UserMirror.FIRST_NAME to user.firstName
 *     UserMirror.LAST_NAME to user.lastName
 *     UserMirror.EMAIL to user.email
 *     UserMirror.UPDATED_AT to now
 *     where {
 *         UserMirror.ID eq user.id
 *     }
 * }.execute()
 * ```
 */
inline fun <reified T : Table> update(noinline block: TableUpdateDslBuilder.() -> Unit): TableUpdateBuilder {
    val table = T::class.objectInstance
        ?: throw IllegalArgumentException("${T::class.simpleName} must be an object (singleton)")
    val dslBuilder = TableUpdateDslBuilder(table)
    dslBuilder.block()
    return dslBuilder.build()
}

/**
 * DELETE with WHERE condition directly in block using reified generics
 *
 * ```kotlin
 * delete<UserMirror> {
 *     UserMirror.ID eq userId
 * }.execute()
 * ```
 */
inline fun <reified T : Table> delete(noinline block: TableWhereBuilder.() -> TablePredicate): TableDeleteBuilder {
    val table = T::class.objectInstance
        ?: throw IllegalArgumentException("${T::class.simpleName} must be an object (singleton)")
    return TableDeleteBuilder(table).where(block)
}

/**
 * Combined DSL builder for UPDATE operations
 *
 * ```kotlin
 * update<UserMirror> {
 *     UserMirror.FIRST_NAME to user.firstName
 *     UserMirror.LAST_NAME to user.lastName
 *     UserMirror.EMAIL to user.email
 *     where {
 *         UserMirror.ID eq user.id
 *     }
 * }.execute()
 * ```
 */
class TableUpdateDslBuilder(private val table: Table) {
    private val updates = mutableMapOf<ColumnRef, Any?>()
    private var predicate: TablePredicate? = null
    private val returningColumns = mutableListOf<ColumnRef>()

    /**
     * Set column value directly
     */
    infix fun ColumnRef.to(value: Any?) {
        updates[this] = value
    }

    /**
     * WHERE clause
     */
    fun where(block: TableWhereBuilder.() -> TablePredicate) {
        predicate = TableWhereBuilder().block()
    }

    /**
     * RETURNING clause (PostgreSQL)
     */
    fun returning(vararg cols: ColumnRef) {
        returningColumns.addAll(cols)
    }

    @PublishedApi
    internal fun build(): TableUpdateBuilder {
        val builder = TableUpdateBuilder(table)
        updates.forEach { (col, value) -> builder.set(col, value) }
        predicate?.let { builder.where { it } }
        if (returningColumns.isNotEmpty()) {
            builder.returning(*returningColumns.toTypedArray())
        }
        return builder
    }
}

// ==================== Entry Point Functions (Instance Syntax) ====================

/**
 * SELECT with WHERE condition directly in block (instance syntax)
 *
 * ```kotlin
 * val users = select(UserMirror) {
 *     UserMirror.NAME eq "John"
 * }.executeAs<User>()
 * ```
 */
fun select(table: Table, block: TableWhereBuilder.() -> TablePredicate): TableSelectBuilder {
    return TableSelectBuilder(table).where(block)
}

/**
 * SELECT all from table (instance syntax)
 */
fun select(table: Table): TableSelectBuilder = TableSelectBuilder(table)

/**
 * INSERT with values (instance syntax)
 */
fun insert(table: Table, block: TableInsertBuilder.() -> Unit): TableInsertBuilder {
    return TableInsertBuilder(table).values(block)
}

/**
 * UPDATE with values and where (instance syntax)
 *
 * ```kotlin
 * update(UserMirror) {
 *     UserMirror.NAME to "John"
 *     where { UserMirror.ID eq userId }
 * }.execute()
 * ```
 */
fun update(table: Table, block: TableUpdateDslBuilder.() -> Unit): TableUpdateBuilder {
    val dslBuilder = TableUpdateDslBuilder(table)
    dslBuilder.block()
    return dslBuilder.build()
}

/**
 * DELETE with WHERE (instance syntax)
 */
fun delete(table: Table, block: TableWhereBuilder.() -> TablePredicate): TableDeleteBuilder {
    return TableDeleteBuilder(table).where(block)
}

// ==================== Entry Point Functions (Fluent Syntax) ====================

/**
 * Starts a SELECT query (fluent/chained style)
 *
 * ```kotlin
 * from(UserMirror)
 *     .select(UserMirror.ID, UserMirror.NAME)
 *     .where { UserMirror.STATUS eq "ACTIVE" }
 *     .executeAs<User>()
 * ```
 */
fun from(table: Table): TableSelectBuilder = TableSelectBuilder(table)

/**
 * Starts an INSERT query (fluent/chained style)
 *
 * ```kotlin
 * into(UserMirror)
 *     .values {
 *         UserMirror.NAME to "John"
 *         UserMirror.EMAIL to "john@example.com"
 *     }
 *     .execute()
 * ```
 */
fun into(table: Table): TableInsertBuilder = TableInsertBuilder(table)

/**
 * Starts an UPDATE query (fluent/chained style)
 *
 * ```kotlin
 * update(UserMirror)
 *     .set { UserMirror.STATUS to "INACTIVE" }
 *     .where { UserMirror.ID eq userId }
 *     .execute()
 * ```
 */
fun update(table: Table): TableUpdateBuilder = TableUpdateBuilder(table)

/**
 * Starts a DELETE query (fluent/chained style)
 *
 * ```kotlin
 * deleteFrom(UserMirror)
 *     .where { UserMirror.ID eq userId }
 *     .execute()
 * ```
 */
fun deleteFrom(table: Table): TableDeleteBuilder = TableDeleteBuilder(table)
