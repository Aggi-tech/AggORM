package com.aggitech.orm.validation

import com.aggitech.orm.entities.annotations.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Resultado da validação de uma entidade
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList()
) {
    fun throwIfInvalid() {
        if (!isValid) {
            throw ValidationException(errors)
        }
    }
}

/**
 * Erro de validação individual
 */
data class ValidationError(
    val property: String,
    val message: String,
    val value: Any?
)

/**
 * Exception lançada quando uma entidade falha na validação
 */
class ValidationException(val errors: List<ValidationError>) : Exception(
    "Validation failed: ${errors.joinToString("; ") { "${it.property}: ${it.message}" }}"
)

/**
 * Validador de entidades baseado em anotações
 */
class EntityValidator {
    /**
     * Valida uma entidade e retorna o resultado
     */
    fun <T : Any> validate(entity: T): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        @Suppress("UNCHECKED_CAST")
        val entityClass = entity::class as KClass<T>

        entityClass.memberProperties.forEach { property ->
            @Suppress("UNCHECKED_CAST")
            val prop = property as KProperty1<T, Any?>
            val value = prop.get(entity)

            errors.addAll(validateProperty(prop, value, entityClass))
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Valida uma propriedade individual
     */
    private fun <T : Any> validateProperty(
        property: KProperty1<T, *>,
        value: Any?,
        entityClass: KClass<T>
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // @NotNull
        property.findAnnotation<NotNull>()?.let { annotation ->
            if (value == null) {
                errors.add(ValidationError(property.name, annotation.message, value))
            }
        }

        // Se o valor é null, não precisa validar outras constraints
        if (value == null) {
            return errors
        }

        // @Size
        property.findAnnotation<Size>()?.let { annotation ->
            val size = when (value) {
                is String -> value.length
                is Collection<*> -> value.size
                is Array<*> -> value.size
                else -> null
            }

            if (size != null) {
                if (size < annotation.min || size > annotation.max) {
                    val message = annotation.message
                        .replace("{min}", annotation.min.toString())
                        .replace("{max}", annotation.max.toString())
                    errors.add(ValidationError(property.name, message, value))
                }
            }
        }

        // @Min
        property.findAnnotation<Min>()?.let { annotation ->
            val numValue = when (value) {
                is Number -> value.toLong()
                else -> null
            }

            if (numValue != null && numValue < annotation.value) {
                val message = annotation.message.replace("{value}", annotation.value.toString())
                errors.add(ValidationError(property.name, message, value))
            }
        }

        // @Max
        property.findAnnotation<Max>()?.let { annotation ->
            val numValue = when (value) {
                is Number -> value.toLong()
                else -> null
            }

            if (numValue != null && numValue > annotation.value) {
                val message = annotation.message.replace("{value}", annotation.value.toString())
                errors.add(ValidationError(property.name, message, value))
            }
        }

        // @Range
        property.findAnnotation<Range>()?.let { annotation ->
            val numValue = when (value) {
                is Number -> value.toDouble()
                else -> null
            }

            if (numValue != null) {
                if (numValue < annotation.min || numValue > annotation.max) {
                    val message = annotation.message
                        .replace("{min}", annotation.min.toString())
                        .replace("{max}", annotation.max.toString())
                    errors.add(ValidationError(property.name, message, value))
                }
            }
        }

        // @Email
        property.findAnnotation<Email>()?.let { annotation ->
            if (value is String) {
                val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()
                if (!emailRegex.matches(value)) {
                    errors.add(ValidationError(property.name, annotation.message, value))
                }
            }
        }

        // @Pattern
        property.findAnnotation<Pattern>()?.let { annotation ->
            if (value is String) {
                val regex = annotation.regex.toRegex()
                if (!regex.matches(value)) {
                    val message = annotation.message.replace("{regex}", annotation.regex)
                    errors.add(ValidationError(property.name, message, value))
                }
            }
        }

        return errors
    }

    /**
     * Valida e lança exceção se inválido
     */
    fun <T : Any> validateAndThrow(entity: T) {
        validate(entity).throwIfInvalid()
    }
}

/**
 * Extensão para validar entidades facilmente
 */
fun <T : Any> T.validate(): ValidationResult {
    return EntityValidator().validate(this)
}

/**
 * Extensão para validar e lançar exceção
 */
fun <T : Any> T.validateAndThrow() {
    EntityValidator().validateAndThrow(this)
}
