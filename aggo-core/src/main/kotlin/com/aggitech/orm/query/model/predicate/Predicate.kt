package com.aggitech.orm.query.model.predicate

import com.aggitech.orm.query.model.operand.Operand

/**
 * Sealed class representando predicados (condições WHERE) em SQL
 */
sealed class Predicate {
    /**
     * Predicado de comparação (=, !=, <, >, <=, >=)
     */
    data class Comparison(
        val left: Operand,
        val operator: ComparisonOperator,
        val right: Operand
    ) : Predicate()

    /**
     * Predicado lógico AND
     */
    data class And(val left: Predicate, val right: Predicate) : Predicate()

    /**
     * Predicado lógico OR
     */
    data class Or(val left: Predicate, val right: Predicate) : Predicate()

    /**
     * Predicado lógico NOT
     */
    data class Not(val predicate: Predicate) : Predicate()

    /**
     * Predicado IN (valor IN lista)
     */
    data class In<T>(val operand: Operand, val values: List<T>) : Predicate()

    /**
     * Predicado NOT IN
     */
    data class NotIn<T>(val operand: Operand, val values: List<T>) : Predicate()

    /**
     * Predicado LIKE (string matching)
     */
    data class Like(val operand: Operand, val pattern: String) : Predicate()

    /**
     * Predicado NOT LIKE
     */
    data class NotLike(val operand: Operand, val pattern: String) : Predicate()

    /**
     * Predicado IS NULL
     */
    data class IsNull(val operand: Operand) : Predicate()

    /**
     * Predicado IS NOT NULL
     */
    data class IsNotNull(val operand: Operand) : Predicate()

    /**
     * Predicado BETWEEN (valor BETWEEN min AND max)
     */
    data class Between<T : Comparable<T>>(
        val operand: Operand,
        val lower: T,
        val upper: T
    ) : Predicate()
}

/**
 * Enum representando operadores de comparação SQL
 */
enum class ComparisonOperator(val symbol: String) {
    EQ("="),
    NE("!="),
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<=")
}
