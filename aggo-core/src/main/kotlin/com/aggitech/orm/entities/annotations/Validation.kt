package com.aggitech.orm.entities.annotations

import kotlin.reflect.KClass

// ==================== Validação de Dados ====================

/**
 * Indica que o campo não pode ser nulo
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class NotNull(val message: String = "Field cannot be null")

/**
 * Indica que o valor deve ser único na tabela
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Unique(val message: String = "Field must be unique")

/**
 * Valida o tamanho de strings ou coleções
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Size(
    val min: Int = 0,
    val max: Int = Int.MAX_VALUE,
    val message: String = "Size must be between {min} and {max}"
)

/**
 * Valida valor mínimo para números
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Min(
    val value: Long,
    val message: String = "Value must be at least {value}"
)

/**
 * Valida valor máximo para números
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Max(
    val value: Long,
    val message: String = "Value must be at most {value}"
)

/**
 * Valida que o valor está dentro de um range (para Double/Float)
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Range(
    val min: Double,
    val max: Double,
    val message: String = "Value must be between {min} and {max}"
)

/**
 * Valida que a string corresponde a um regex
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Pattern(
    val regex: String,
    val message: String = "Value must match pattern {regex}"
)

/**
 * Valida formato de email
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Email(
    val message: String = "Invalid email format"
)

// ==================== Metadados de Banco de Dados ====================

/**
 * Indica que o campo é chave primária
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class PrimaryKey

/**
 * Define uma chave estrangeira
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ForeignKey(
    val references: KClass<*>,
    val onDelete: CascadeType = CascadeType.NO_ACTION,
    val onUpdate: CascadeType = CascadeType.NO_ACTION
)

/**
 * Configura metadados de uma coluna
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Column(
    val name: String = "",
    val nullable: Boolean = true,
    val unique: Boolean = false,
    val length: Int = 255,
    val precision: Int = 0,
    val scale: Int = 0
)

/**
 * Define nome e schema da tabela
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Table(
    val name: String = "",
    val schema: String = "public"
)

/**
 * Define uma constraint UNIQUE composta
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class UniqueConstraint(
    val columns: Array<String>,
    val name: String = ""
)

/**
 * Define um índice na tabela
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class Index(
    val columns: Array<String>,
    val name: String = "",
    val unique: Boolean = false
)

/**
 * Enum para tipos de cascade em chaves estrangeiras
 */
enum class CascadeType {
    /**
     * Não faz nada (padrão)
     */
    NO_ACTION,

    /**
     * Impede a operação se houver registros relacionados
     */
    RESTRICT,

    /**
     * Propaga a operação (DELETE ou UPDATE) para os registros relacionados
     */
    CASCADE,

    /**
     * Define como NULL os registros relacionados
     */
    SET_NULL,

    /**
     * Define como valor padrão os registros relacionados
     */
    SET_DEFAULT
}
