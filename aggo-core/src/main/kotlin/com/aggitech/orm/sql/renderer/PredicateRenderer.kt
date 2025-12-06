package com.aggitech.orm.sql.renderer

import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.query.model.operand.Operand
import com.aggitech.orm.query.model.predicate.Predicate
import com.aggitech.orm.sql.context.RenderContext

/**
 * Renderiza predicados WHERE em SQL (DRY - compartilhado entre renderers)
 */
class PredicateRenderer(
    @Suppress("unused") private val dialect: SqlDialect
) {

    /**
     * Renderiza um predicado WHERE completo
     */
    fun render(predicate: Predicate, ctx: RenderContext): String {
        return when (predicate) {
            is Predicate.Comparison -> {
                val left = renderOperand(predicate.left, ctx)
                val right = renderOperand(predicate.right, ctx)
                "$left ${predicate.operator.symbol} $right"
            }
            is Predicate.And -> {
                "(${render(predicate.left, ctx)} AND ${render(predicate.right, ctx)})"
            }
            is Predicate.Or -> {
                "(${render(predicate.left, ctx)} OR ${render(predicate.right, ctx)})"
            }
            is Predicate.Not -> {
                "NOT (${render(predicate.predicate, ctx)})"
            }
            is Predicate.Like -> {
                val operand = renderOperand(predicate.operand, ctx)
                val param = ctx.addParameter(predicate.pattern)
                "$operand LIKE $param"
            }
            is Predicate.NotLike -> {
                val operand = renderOperand(predicate.operand, ctx)
                val param = ctx.addParameter(predicate.pattern)
                "$operand NOT LIKE $param"
            }
            is Predicate.In<*> -> {
                val operand = renderOperand(predicate.operand, ctx)
                val params = ctx.addParameters(predicate.values)
                "$operand IN ($params)"
            }
            is Predicate.NotIn<*> -> {
                val operand = renderOperand(predicate.operand, ctx)
                val params = ctx.addParameters(predicate.values)
                "$operand NOT IN ($params)"
            }
            is Predicate.IsNull -> {
                "${renderOperand(predicate.operand, ctx)} IS NULL"
            }
            is Predicate.IsNotNull -> {
                "${renderOperand(predicate.operand, ctx)} IS NOT NULL"
            }
            is Predicate.Between<*> -> {
                val operand = renderOperand(predicate.operand, ctx)
                val lower = ctx.addParameter(predicate.lower)
                val upper = ctx.addParameter(predicate.upper)
                "$operand BETWEEN $lower AND $upper"
            }
        }
    }

    /**
     * Renderiza um operando (propriedade, literal ou parâmetro)
     */
    fun renderOperand(operand: Operand, ctx: RenderContext): String {
        return when (operand) {
            is Operand.Property<*, *> -> ctx.qualifyColumn(operand.entity, operand.property)
            is Operand.Literal<*> -> ctx.addParameter(operand.value)
            is Operand.Parameter -> "?"
        }
    }
}
