package com.aggitech.orm.migrations.dsl

import com.aggitech.orm.entities.annotations.Column
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

/**
 * Utilities for extracting column names from KProperty references.
 */
object PropertyUtils {

    /**
     * Extracts the column name from a KProperty.
     *
     * If the property has a @Column annotation with a non-empty name, uses that name.
     * Otherwise, converts the property name from camelCase to snake_case.
     *
     * Examples:
     * - Property "userId" -> "user_id"
     * - Property "id" with @Column(name = "user_id") -> "user_id"
     *
     * @param property The KProperty to extract the column name from
     * @return The column name
     */
    fun <T, R> getColumnName(property: KProperty1<T, R>): String {
        val annotation = property.findAnnotation<Column>()
        return if (annotation != null && annotation.name.isNotEmpty()) {
            annotation.name
        } else {
            property.name.toSnakeCase()
        }
    }

    /**
     * Converts a camelCase string to snake_case.
     *
     * Examples:
     * - "userId" -> "user_id"
     * - "createdAt" -> "created_at"
     * - "HTTPResponse" -> "http_response"
     */
    private fun String.toSnakeCase(): String {
        return this
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1_$2")
            .lowercase()
    }
}
