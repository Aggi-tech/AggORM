package com.aggitech.orm.query.model.field

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Sealed class representando campos no SELECT
 */
sealed class SelectField {
    /**
     * Campo representando uma propriedade de uma entidade
     */
    data class Property<T : Any, R>(
        val entity: KClass<T>,
        val property: KProperty1<T, R>,
        val alias: String? = null
    ) : SelectField()

    /**
     * Campo representando uma função de agregação (COUNT, SUM, AVG, MAX, MIN)
     */
    data class Aggregate(
        val function: AggregateFunction,
        val field: SelectField,
        val alias: String? = null
    ) : SelectField()

    /**
     * Representa SELECT *
     */
    object All : SelectField()

    /**
     * Expressão SQL customizada
     */
    data class Expression(
        val sql: String,
        val alias: String? = null
    ) : SelectField()
}

/**
 * Enum representando funções de agregação SQL
 */
enum class AggregateFunction {
    COUNT,
    SUM,
    AVG,
    MAX,
    MIN,
    COUNT_DISTINCT
}
