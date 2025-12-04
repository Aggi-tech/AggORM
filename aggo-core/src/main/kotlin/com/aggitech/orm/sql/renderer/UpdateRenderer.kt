package com.aggitech.orm.sql.renderer

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.query.model.UpdateQuery
import com.aggitech.orm.sql.context.RenderContext
import com.aggitech.orm.sql.context.RenderedSql

/**
 * Renderiza queries UPDATE em SQL
 */
class UpdateRenderer(
    private val dialect: SqlDialect
) : QueryRenderer<UpdateQuery<*>> {

    override fun render(query: UpdateQuery<*>): RenderedSql {
        val context = RenderContext(dialect)

        val tableName = EntityRegistry.resolveTable(query.table)

        if (query.updates.isEmpty()) {
            throw IllegalArgumentException("Cannot UPDATE without values")
        }

        val setClause = query.updates.entries.joinToString(", ") { (column, value) ->
            "$column = ${context.addParameter(value)}"
        }

        val sql = buildString {
            append("UPDATE $tableName SET $setClause")

            query.where?.let { predicate ->
                append(" WHERE ")
                // Reusa o renderizador de predicados do SelectRenderer
                val selectRenderer = com.aggitech.orm.sql.renderer.SelectRenderer(dialect)
                append(selectRenderer.renderPredicate(predicate, context))
            }
        }

        return RenderedSql(sql, context.parameters)
    }
}
