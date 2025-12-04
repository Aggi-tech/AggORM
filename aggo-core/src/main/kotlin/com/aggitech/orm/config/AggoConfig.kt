package com.aggitech.orm.config

import com.aggitech.orm.enums.SupportedDatabases


data class DbConfig(
    val database: String,
    val host: String = "localhost",
    val port: Int = 5432,
    val user: String,
    val password: String,
    val type: SupportedDatabases = SupportedDatabases.POSTGRESQL
) {
    val url: String
        get() = type.jdbcUrl(host, port, database)
}

fun interface ConnectionFactory {
    fun open(): java.sql.Connection
}

class JdbcConnectionFactory(private val config: DbConfig) : ConnectionFactory {
    override fun open() =
        java.sql.DriverManager.getConnection(config.url, config.user, config.password)
}

