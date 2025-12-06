package com.aggitech.orm.sql.renderer

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.query.model.InsertQuery
import com.aggitech.orm.sql.context.RenderContext
import com.aggitech.orm.sql.context.RenderedSql
import kotlin.reflect.full.memberProperties

/**
 * Renderiza queries INSERT em SQL
 */
class InsertRenderer(
    private val dialect: SqlDialect
) : QueryRenderer<InsertQuery<*>> {

    override fun render(query: InsertQuery<*>): RenderedSql {
        val context = RenderContext(dialect)

        val tableName = context.quote(EntityRegistry.resolveTable(query.into))

        // Se values está vazio, tenta pegar os valores do objeto entity
        val values = if (query.values.isEmpty() && query.entity != null) {
            extractValuesFromEntity(query.entity, context)
        } else {
            query.values.mapKeys { (key, _) -> context.quote(key) }
        }

        if (values.isEmpty()) {
            throw IllegalArgumentException("Cannot INSERT without values")
        }

        val columns = values.keys.joinToString(", ")
        val placeholders = values.values.joinToString(", ") { value ->
            context.addParameter(value)
        }

        val sql = "INSERT INTO $tableName ($columns) VALUES ($placeholders)"

        return RenderedSql(sql, context.parameters)
    }

    /**
     * Extrai valores de uma instância de entidade usando reflection
     */
    private fun extractValuesFromEntity(entity: Any, context: RenderContext): Map<String, Any?> {
        val values = mutableMapOf<String, Any?>()

        entity::class.memberProperties.forEach { prop ->
            val columnName = context.quote(EntityRegistry.resolveColumn(prop))
            val value = prop.getter.call(entity)

            // Não inclui propriedades nulas que são chave primária auto-gerada
            if (value != null || !isPrimaryKey(prop)) {
                values[columnName] = value
            }
        }

        return values
    }

    private fun isPrimaryKey(prop: kotlin.reflect.KProperty<*>): Boolean {
        return prop.annotations.any {
            it.annotationClass.simpleName == "PrimaryKey"
        }
    }
}
