package com.aggitech.orm.query.model

import com.aggitech.orm.query.model.field.SelectField
import com.aggitech.orm.query.model.ordering.PropertyOrder
import com.aggitech.orm.query.model.predicate.Predicate
import com.aggitech.orm.dsl.JoinClause
import kotlin.reflect.KClass

/**
 * Sealed class base para todas as queries
 */
sealed class Query

/**
 * Modelo imutável representando uma query SELECT
 */
data class SelectQuery<T : Any>(
    val from: KClass<T>,
    val fields: List<SelectField> = emptyList(),
    val joins: List<JoinClause> = emptyList(),
    val where: Predicate? = null,
    val groupBy: List<SelectField> = emptyList(),
    val having: Predicate? = null,
    val orderBy: List<PropertyOrder<*, *>> = emptyList(),
    val limit: Int? = null,
    val offset: Int? = null,
    val distinct: Boolean = false
) : Query()

/**
 * Modelo representando uma query INSERT
 */
data class InsertQuery<T : Any>(
    val into: KClass<T>,
    val values: Map<String, Any?> = emptyMap(),
    val entity: T? = null
) : Query()

/**
 * Modelo representando uma query UPDATE
 */
data class UpdateQuery<T : Any>(
    val table: KClass<T>,
    val updates: Map<String, Any?>,
    val where: Predicate? = null
) : Query()

/**
 * Modelo representando uma query DELETE
 */
data class DeleteQuery<T : Any>(
    val from: KClass<T>,
    val where: Predicate? = null
) : Query()
