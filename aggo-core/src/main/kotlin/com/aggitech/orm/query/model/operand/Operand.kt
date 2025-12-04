package com.aggitech.orm.query.model.operand

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Sealed class representando operandos em expressões SQL
 */
sealed class Operand {
    /**
     * Operando que representa uma propriedade de uma entidade
     */
    data class Property<T : Any, R>(
        val entity: KClass<T>,
        val property: KProperty1<T, R>
    ) : Operand()

    /**
     * Operando que representa um valor literal
     */
    data class Literal<T>(val value: T) : Operand()

    /**
     * Operando que representa um parâmetro de prepared statement
     */
    data class Parameter(val index: Int) : Operand()
}
