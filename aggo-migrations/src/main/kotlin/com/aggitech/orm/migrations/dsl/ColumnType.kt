package com.aggitech.orm.migrations.dsl

import kotlin.reflect.KClass

/**
 * Represents SQL column types in a database-agnostic way.
 * Each type can be rendered to database-specific SQL.
 */
sealed class ColumnType {
    // String types
    data class Varchar(val length: Int = 255) : ColumnType()
    data class Char(val length: Int) : ColumnType()
    data object Text : ColumnType()

    // Numeric types
    data object Integer : ColumnType()
    data object BigInteger : ColumnType()
    data object SmallInteger : ColumnType()
    data object Serial : ColumnType()
    data object BigSerial : ColumnType()
    data class Decimal(val precision: Int, val scale: Int) : ColumnType()
    data object Float : ColumnType()
    data object Double : ColumnType()

    // Boolean
    data object Boolean : ColumnType()

    // Date/Time types
    data object Date : ColumnType()
    data object Time : ColumnType()
    data object Timestamp : ColumnType()
    data object TimestampTz : ColumnType()

    // Binary types
    data class Binary(val length: Int? = null) : ColumnType()
    data object Blob : ColumnType()

    // JSON types
    data object Json : ColumnType()
    data object Jsonb : ColumnType()

    // UUID
    data object Uuid : ColumnType()

    // Enum type
    /**
     * Represents an ENUM type with a list of allowed values.
     * In PostgreSQL, this creates a native ENUM type.
     * In MySQL, this uses the ENUM column type.
     *
     * @param typeName The name of the enum type (used for PostgreSQL CREATE TYPE)
     * @param values The list of allowed enum values
     */
    data class Enum(val typeName: String, val values: List<String>) : ColumnType() {
        constructor(typeName: String, vararg values: String) : this(typeName, values.toList())

        /**
         * Creates an Enum ColumnType from a Kotlin enum class.
         * Uses the enum class simple name as the type name and extracts all enum constant names.
         */
        companion object {
            inline fun <reified E : kotlin.Enum<E>> fromEnum(): Enum {
                val enumClass = E::class
                val typeName = enumClass.simpleName?.lowercase() ?: "enum_type"
                val values = enumValues<E>().map { it.name }
                return Enum(typeName, values)
            }

            fun <E : kotlin.Enum<E>> fromEnum(enumClass: Class<E>): Enum {
                val typeName = enumClass.simpleName.lowercase()
                val values = enumClass.enumConstants.map { it.name }
                return Enum(typeName, values)
            }
        }
    }
}
