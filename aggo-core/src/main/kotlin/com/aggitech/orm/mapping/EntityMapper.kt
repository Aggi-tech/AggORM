package com.aggitech.orm.mapping

import com.aggitech.orm.core.metadata.EntityRegistry
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

/**
 * Mapeador de resultados de queries para entidades/DTOs
 *
 * Usa reflexão para mapear Map<String, Any?> para instâncias de classes.
 * Leve e sem dependências de frameworks.
 */
object EntityMapper {

    /**
     * Converte uma string CamelCase para snake_case
     */
    private fun String.toSnakeCase(): String {
        return this
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1_$2")
            .lowercase()
    }

    /**
     * Mapeia um Map para uma instância de entidade
     *
     * Uso:
     * ```kotlin
     * val row: Map<String, Any?> = mapOf("id" to 1L, "name" to "John", "age" to 30)
     * val user: User = EntityMapper.map(row, User::class)
     * ```
     *
     * @param row Mapa de coluna -> valor
     * @param entityClass Classe da entidade
     * @return Instância da entidade preenchida
     */
    fun <T : Any> map(row: Map<String, Any?>, entityClass: KClass<T>): T {
        val constructor = entityClass.primaryConstructor
            ?: throw IllegalArgumentException(
                "Class ${entityClass.simpleName} must have a primary constructor"
            )

        val args = mutableMapOf<KParameter, Any?>()

        for (param in constructor.parameters) {
            // Tenta encontrar o valor no mapa
            // Converte camelCase para snake_case automaticamente
            val paramName = param.name!!
            val columnName = paramName.toSnakeCase()
            val value = row[columnName] ?: row[paramName]

            // Converte o valor para o tipo esperado
            val convertedValue = convertValue(value, param.type.jvmErasure)

            if (convertedValue != null || param.type.isMarkedNullable) {
                args[param] = convertedValue
            } else if (!param.isOptional) {
                throw IllegalArgumentException(
                    "Required parameter '${param.name}' not found in result for class ${entityClass.simpleName}"
                )
            }
        }

        return constructor.callBy(args)
    }

    /**
     * Mapeia uma lista de Maps para uma lista de entidades
     */
    fun <T : Any> mapList(rows: List<Map<String, Any?>>, entityClass: KClass<T>): List<T> {
        return rows.map { map(it, entityClass) }
    }

    /**
     * Converte um valor para o tipo esperado
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertValue(value: Any?, targetType: KClass<*>): Any? {
        if (value == null) return null

        return when (targetType) {
            String::class -> value.toString()
            Int::class -> when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
            Long::class -> when (value) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                else -> null
            }
            Double::class -> when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
            Float::class -> when (value) {
                is Number -> value.toFloat()
                is String -> value.toFloatOrNull()
                else -> null
            }
            Boolean::class -> when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.toBoolean()
                else -> null
            }
            else -> {
                // Suporte para Enums
                if (targetType.java.isEnum) {
                    val enumClass = targetType.java as Class<out Enum<*>>
                    val enumName = value.toString()
                    enumClass.enumConstants.firstOrNull { it.name == enumName }
                }
                // Tenta cast direto
                else if (targetType.isInstance(value)) {
                    value
                } else {
                    null
                }
            }
        }
    }
}

/**
 * Extensão para mapear resultado diretamente
 *
 * Uso:
 * ```kotlin
 * val row: Map<String, Any?> = ...
 * val user: User = row.toEntity()
 * ```
 */
inline fun <reified T : Any> Map<String, Any?>.toEntity(): T {
    return EntityMapper.map(this, T::class)
}

/**
 * Extensão para mapear lista de resultados
 *
 * Uso:
 * ```kotlin
 * val rows: List<Map<String, Any?>> = select<User> { ... }.execute()
 * val users: List<User> = rows.toEntities()
 * ```
 */
inline fun <reified T : Any> List<Map<String, Any?>>.toEntities(): List<T> {
    return EntityMapper.mapList(this, T::class)
}
