package com.aggitech.orm.sql.renderer

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.query.model.DeleteQuery
import com.aggitech.orm.sql.context.RenderContext
import com.aggitech.orm.sql.context.RenderedSql

/**
 * Renderiza queries DELETE em SQL
 */
class DeleteRenderer(
    private val dialect: SqlDialect
) : QueryRenderer<DeleteQuery<*>> {

    private val predicateRenderer = PredicateRenderer(dialect)

    override fun render(query: DeleteQuery<*>): RenderedSql {
        val context = RenderContext(dialect)

        val tableName = EntityRegistry.resolveTable(query.from)

        val sql = buildString {
            append("DELETE FROM $tableName")

            query.where?.let { predicate ->
                append(" WHERE ")
                append(predicateRenderer.render(predicate, context))
            }
        }

        return RenderedSql(sql, context.parameters)
    }
}
