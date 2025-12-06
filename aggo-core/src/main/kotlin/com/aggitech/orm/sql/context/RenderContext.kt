package com.aggitech.orm.sql.context

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.enums.SqlDialect
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Contexto usado durante a renderização de SQL.
 * Mantém o estado de parâmetros e oferece métodos auxiliares para renderização.
 */
class RenderContext(val dialect: SqlDialect) {
    /**
     * Lista de parâmetros acumulados durante a renderização.
     * Serão bind em PreparedStatement na ordem que aparecem.
     */
    val parameters = mutableListOf<Any?>()

    /**
     * Contador de aliases gerados automaticamente
     */
    private var aliasCounter = 0

    /**
     * Adiciona um parâmetro e retorna o placeholder (?)
     */
    fun addParameter(value: Any?): String {
        parameters.add(value)
        return "?"
    }

    /**
     * Adiciona múltiplos parâmetros e retorna uma string de placeholders separados por vírgula
     */
    fun addParameters(values: List<Any?>): String {
        return values.joinToString(", ") { addParameter(it) }
    }

    /**
     * Gera um alias único
     */
    fun generateAlias(): String {
        return "t${++aliasCounter}"
    }

    /**
     * Quota um identificador SQL (tabela ou coluna) conforme o dialeto
     */
    fun quote(identifier: String): String {
        val quoteChar = dialect.quoteChar
        return "$quoteChar$identifier$quoteChar"
    }

    /**
     * Retorna o nome qualificado de uma coluna (tabela.coluna) - DRY helper
     */
    fun qualifyColumn(entity: KClass<*>, property: KProperty1<*, *>): String {
        val table = EntityRegistry.resolveTable(entity)
        val column = EntityRegistry.resolveColumn(property)
        return "$table.$column"
    }
}

/**
 * Resultado da renderização de uma query SQL.
 * Contém o SQL gerado e os parâmetros para bind.
 */
data class RenderedSql(
    val sql: String,
    val parameters: List<Any?>
) {
    /**
     * Retorna o SQL com placeholders substituídos pelos valores (para debug).
     * CUIDADO: Não usar em produção (SQL injection risk)
     */
    fun toDebugString(): String {
        var debugSql = sql
        parameters.forEach { param ->
            val replacement = when (param) {
                is String -> "'$param'"
                null -> "NULL"
                else -> param.toString()
            }
            debugSql = debugSql.replaceFirst("?", replacement)
        }
        return debugSql
    }

    override fun toString(): String {
        return "RenderedSql(sql='$sql', parameters=$parameters)"
    }
}
