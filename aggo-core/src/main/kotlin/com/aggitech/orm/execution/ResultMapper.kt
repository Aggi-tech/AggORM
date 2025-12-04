package com.aggitech.orm.execution

import com.aggitech.orm.core.metadata.EntityRegistry
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.memberProperties

/**
 * Mapeia resultados de queries para instâncias de entidades
 */
class ResultMapper {
    /**
     * Mapeia uma lista de mapas (resultados da query) para uma lista de entidades
     */
    fun <T : Any> mapToEntities(
        results: List<Map<String, Any?>>,
        entityClass: KClass<T>
    ): List<T> {
        return results.map { row ->
            mapToEntity(row, entityClass)
        }
    }

    /**
     * Mapeia um único mapa para uma entidade
     */
    fun <T : Any> mapToEntity(
        row: Map<String, Any?>,
        entityClass: KClass<T>
    ): T {
        val constructor = entityClass.primaryConstructor
            ?: throw IllegalArgumentException("Entity ${entityClass.simpleName} must have a primary constructor")

        // Mapeia parâmetros do construtor
        val args = constructor.parameters.associateWith { param ->
            val columnName = findColumnName(param, entityClass)
            convertValue(row[columnName], param)
        }

        return constructor.callBy(args)
    }

    /**
     * Encontra o nome da coluna correspondente ao parâmetro
     */
    private fun <T : Any> findColumnName(param: KParameter, entityClass: KClass<T>): String {
        // Tenta encontrar a propriedade correspondente ao parâmetro
        val property = entityClass.memberProperties.find { it.name == param.name }

        return if (property != null) {
            // Usa o EntityRegistry para resolver o nome da coluna
            EntityRegistry.resolveColumn(property)
        } else {
            // Fallback: usa o nome do parâmetro convertido para snake_case
            param.name?.toSnakeCase() ?: param.name ?: ""
        }
    }

    /**
     * Converte o valor do banco de dados para o tipo esperado pelo parâmetro
     */
    private fun convertValue(value: Any?, param: KParameter): Any? {
        if (value == null) {
            return if (param.type.isMarkedNullable) null
            else throw IllegalArgumentException("Parameter ${param.name} cannot be null")
        }

        val paramType = param.type.classifier as? KClass<*>
            ?: return value

        return when (paramType) {
            String::class -> value.toString()
            Int::class -> when (value) {
                is Number -> value.toInt()
                is String -> value.toInt()
                else -> value
            }
            Long::class -> when (value) {
                is Number -> value.toLong()
                is String -> value.toLong()
                else -> value
            }
            Double::class -> when (value) {
                is Number -> value.toDouble()
                is String -> value.toDouble()
                else -> value
            }
            Float::class -> when (value) {
                is Number -> value.toFloat()
                is String -> value.toFloat()
                else -> value
            }
            Boolean::class -> when (value) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> value.toBoolean()
                else -> value
            }
            else -> value
        }
    }

    /**
     * Converte string para snake_case
     */
    private fun String.toSnakeCase(): String {
        return this
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1_$2")
            .lowercase()
    }
}

/**
 * Extensão para facilitar o uso do ResultMapper
 */
inline fun <reified T : Any> List<Map<String, Any?>>.mapToEntities(): List<T> {
    return ResultMapper().mapToEntities(this, T::class)
}

/**
 * Extensão para mapear um único resultado
 */
inline fun <reified T : Any> Map<String, Any?>.mapToEntity(): T {
    return ResultMapper().mapToEntity(this, T::class)
}
