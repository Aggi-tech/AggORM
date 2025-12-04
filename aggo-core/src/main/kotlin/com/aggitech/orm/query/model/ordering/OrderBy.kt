package com.aggitech.orm.query.model.ordering

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Representa uma cláusula ORDER BY
 */
data class OrderBy(
    val column: String,
    val direction: OrderDirection
)

/**
 * Representa uma ordenação baseada em propriedade
 */
data class PropertyOrder<T : Any, R>(
    val entity: KClass<T>,
    val property: KProperty1<T, R>,
    val direction: OrderDirection
)

/**
 * Enum representando direção de ordenação
 */
enum class OrderDirection {
    ASC,
    DESC
}
