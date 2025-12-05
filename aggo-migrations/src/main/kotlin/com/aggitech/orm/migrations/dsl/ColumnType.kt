package com.aggitech.orm.migrations.dsl

/**
 * Represents SQL column types in a database-agnostic way.
 * Each type can be rendered to database-specific SQL.
 */
sealed class ColumnType {
    data class Varchar(val length: Int = 255) : ColumnType()
    data class Char(val length: Int) : ColumnType()
    data object Text : ColumnType()
    data object Integer : ColumnType()
    data object BigInteger : ColumnType()
    data object SmallInteger : ColumnType()
    data object Boolean : ColumnType()
    data class Decimal(val precision: Int, val scale: Int) : ColumnType()
    data object Float : ColumnType()
    data object Double : ColumnType()
    data object Date : ColumnType()
    data object Time : ColumnType()
    data object Timestamp : ColumnType()
    data class Binary(val length: Int? = null) : ColumnType()
    data object Blob : ColumnType()
    data object Json : ColumnType()
    data object Jsonb : ColumnType()
    data object Uuid : ColumnType()
}
