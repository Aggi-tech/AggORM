package com.aggitech.orm.sql.renderer

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.dsl.JoinClause
import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.query.model.SelectQuery
import com.aggitech.orm.query.model.field.SelectField
import com.aggitech.orm.query.model.ordering.PropertyOrder
import com.aggitech.orm.query.model.predicate.Predicate
import com.aggitech.orm.sql.context.RenderContext
import com.aggitech.orm.sql.context.RenderedSql

/**
 * Renderiza queries SELECT em SQL
 */
class SelectRenderer(
    private val dialect: SqlDialect
) : QueryRenderer<SelectQuery<*>> {

    private val predicateRenderer = PredicateRenderer(dialect)

    override fun render(query: SelectQuery<*>): RenderedSql {
        val context = RenderContext(dialect)

        val sql = buildString {
            append("SELECT ")
            if (query.distinct) append("DISTINCT ")

            // Render fields
            append(renderFields(query.fields, query.from, context))

            // FROM clause
            append(" FROM ")
            append(renderTable(query.from, context))

            // JOIN clauses
            query.joins.forEach { join ->
                append(" ")
                append(renderJoin(join, context))
            }

            // WHERE clause
            query.where?.let { predicate ->
                append(" WHERE ")
                append(predicateRenderer.render(predicate, context))
            }

            // GROUP BY
            if (query.groupBy.isNotEmpty()) {
                append(" GROUP BY ")
                append(renderGroupBy(query.groupBy, context))
            }

            // HAVING
            query.having?.let { predicate ->
                append(" HAVING ")
                append(predicateRenderer.render(predicate, context))
            }

            // ORDER BY
            if (query.orderBy.isNotEmpty()) {
                append(" ORDER BY ")
                append(renderOrderBy(query.orderBy, context))
            }

            // LIMIT
            query.limit?.let { append(" LIMIT $it") }

            // OFFSET
            query.offset?.let { append(" OFFSET $it") }
        }

        return RenderedSql(sql, context.parameters)
    }

    /**
     * Renderiza a lista de campos do SELECT
     */
    private fun renderFields(fields: List<SelectField>, fromClass: kotlin.reflect.KClass<*>, ctx: RenderContext): String {
        if (fields.isEmpty()) return "*"

        return fields.joinToString(", ") { field ->
            when (field) {
                is SelectField.Property<*, *> -> {
                    val qualified = ctx.qualifyColumn(field.entity, field.property)
                    field.alias?.let { "$qualified AS $it" } ?: qualified
                }
                is SelectField.Aggregate -> {
                    val inner = when (val innerField = field.field) {
                        is SelectField.Property<*, *> -> ctx.qualifyColumn(innerField.entity, innerField.property)
                        SelectField.All -> "*"
                        else -> renderFields(listOf(innerField), fromClass, ctx)
                    }
                    val agg = "${field.function.name}($inner)"
                    field.alias?.let { "$agg AS $it" } ?: agg
                }
                SelectField.All -> "*"
                is SelectField.Expression -> {
                    field.alias?.let { "${field.sql} AS $it" } ?: field.sql
                }
            }
        }
    }

    /**
     * Renderiza o nome da tabela no FROM
     */
    private fun renderTable(kClass: kotlin.reflect.KClass<*>, ctx: RenderContext): String {
        return EntityRegistry.resolveTable(kClass)
    }

    /**
     * Renderiza uma cláusula JOIN
     */
    private fun renderJoin(join: JoinClause, ctx: RenderContext): String {
        return buildString {
            append(join.type.name)
            append(" JOIN ")
            append(join.targetTable)
            append(" ON ")
            append(join.condition)
        }
    }

    /**
     * Renderiza um predicado WHERE
     * @deprecated Use PredicateRenderer diretamente para melhor desacoplamento
     */
    @Deprecated("Use PredicateRenderer.render() diretamente", ReplaceWith("PredicateRenderer(dialect).render(predicate, ctx)"))
    fun renderPredicate(predicate: Predicate, ctx: RenderContext): String {
        return predicateRenderer.render(predicate, ctx)
    }

    /**
     * Renderiza GROUP BY
     */
    private fun renderGroupBy(fields: List<SelectField>, ctx: RenderContext): String {
        return fields.joinToString(", ") { field ->
            when (field) {
                is SelectField.Property<*, *> -> ctx.qualifyColumn(field.entity, field.property)
                is SelectField.Expression -> field.sql
                else -> throw IllegalArgumentException("Cannot GROUP BY ${field::class.simpleName}")
            }
        }
    }

    /**
     * Renderiza ORDER BY
     */
    private fun renderOrderBy(orderings: List<PropertyOrder<*, *>>, ctx: RenderContext): String {
        return orderings.joinToString(", ") { order ->
            "${ctx.qualifyColumn(order.entity, order.property)} ${order.direction.name}"
        }
    }
}
