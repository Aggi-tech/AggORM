package com.aggitech.orm.core.metadata

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Registry central para metadados de entidades.
 * Converte automaticamente nomes de classes/propriedades para snake_case.
 */
object EntityRegistry {
    private val tableCache = mutableMapOf<KClass<*>, String>()
    private val columnCache = mutableMapOf<KProperty1<*, *>, String>()

    /**
     * Resolve o nome da tabela para uma classe de entidade.
     * Converte o nome da classe para snake_case automaticamente.
     *
     * Exemplo: UserProfile -> user_profile
     */
    fun <T : Any> resolveTable(kClass: KClass<T>): String {
        return tableCache.getOrPut(kClass) {
            kClass.simpleName!!.toSnakeCase()
        }
    }

    /**
     * Resolve o nome da coluna para uma propriedade.
     * Converte o nome da propriedade para snake_case automaticamente.
     *
     * Exemplo: firstName -> first_name
     */
    fun resolveColumn(property: KProperty1<*, *>): String {
        return columnCache.getOrPut(property) {
            property.name.toSnakeCase()
        }
    }

    /**
     * Limpa o cache de metadados (útil para testes)
     */
    fun clearCache() {
        tableCache.clear()
        columnCache.clear()
    }

    /**
     * Converte uma string CamelCase para snake_case.
     *
     * Exemplos:
     * - "User" -> "user"
     * - "UserProfile" -> "user_profile"
     * - "firstName" -> "first_name"
     * - "HTTPSConnection" -> "https_connection"
     */
    private fun String.toSnakeCase(): String {
        return this
            // Adiciona underscore antes de maiúsculas precedidas por minúsculas
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            // Adiciona underscore antes de maiúsculas seguidas por minúsculas
            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1_$2")
            .lowercase()
    }
}
