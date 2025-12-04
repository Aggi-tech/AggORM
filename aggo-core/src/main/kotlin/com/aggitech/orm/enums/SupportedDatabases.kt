package com.aggitech.orm.enums

enum class SupportedDatabases(
    val dialect: SqlDialect,
    val driver: String,
    val defaultPort: Int
) {
    POSTGRESQL(
        dialect = PostgresDialect,
        driver = "org.postgresql.Driver",
        defaultPort = 5432
    ),
    MYSQL(
        dialect = MySqlDialect,
        driver = "com.mysql.cj.jdbc.Driver",
        defaultPort = 3306
    );

    fun jdbcUrl(host: String, port: Int, database: String): String {
        return when (this) {
            POSTGRESQL -> "jdbc:postgresql://$host:$port/$database"
            MYSQL -> "jdbc:mysql://$host:$port/$database"
        }
    }
}

interface SqlDialect {
    val quoteChar: Char
}

object PostgresDialect : SqlDialect {
    override val quoteChar = '"'
}

object MySqlDialect : SqlDialect {
    override val quoteChar = '`'
}

