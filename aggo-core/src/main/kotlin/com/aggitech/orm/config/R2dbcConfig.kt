package com.aggitech.orm.config

import com.aggitech.orm.enums.SupportedDatabases

/**
 * Configuração para conexões R2DBC reativas
 * Minimalista - sem dependência de frameworks específicos
 */
data class R2dbcConfig(
    val database: String,
    val host: String = "localhost",
    val port: Int? = null,
    val user: String,
    val password: String,
    val type: SupportedDatabases,

    // Opções adicionais do driver (ex: ssl, timezone)
    val options: Map<String, String> = emptyMap()
) {
    /**
     * Porta padrão baseada no tipo de banco
     */
    fun getPort(): Int = port ?: when (type) {
        SupportedDatabases.POSTGRESQL -> 5432
        SupportedDatabases.MYSQL -> 3306
    }

    /**
     * Driver R2DBC baseado no tipo de banco
     */
    fun getDriver(): String = when (type) {
        SupportedDatabases.POSTGRESQL -> "postgresql"
        SupportedDatabases.MYSQL -> "mysql"
    }

    /**
     * URL de conexão R2DBC
     */
    fun getUrl(): String {
        val baseUrl = "r2dbc:${getDriver()}://$host:${getPort()}/$database"
        return if (options.isEmpty()) {
            baseUrl
        } else {
            val queryString = options.entries.joinToString("&") { "${it.key}=${it.value}" }
            "$baseUrl?$queryString"
        }
    }

    /**
     * SqlDialect baseado no tipo de banco
     */
    val dialect: com.aggitech.orm.enums.SqlDialect
        get() = type.dialect
}
