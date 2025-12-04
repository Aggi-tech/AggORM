package com.aggitech.orm.r2dbc

import com.aggitech.orm.config.R2dbcConfig
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.reactive.awaitSingle

/**
 * Factory para gerenciar conexões R2DBC usando Coroutines
 *
 * Uso:
 * ```kotlin
 * val factory = R2dbcConnectionFactory(config)
 * val connection = factory.create() // suspend function - aguarda conexão
 * try {
 *     // usar conexão
 * } finally {
 *     connection.close()
 * }
 * ```
 */
class R2dbcConnectionFactory(private val config: R2dbcConfig) {

    private val connectionFactory: ConnectionFactory by lazy {
        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, config.getDriver())
            .option(ConnectionFactoryOptions.HOST, config.host)
            .option(ConnectionFactoryOptions.PORT, config.getPort())
            .option(ConnectionFactoryOptions.DATABASE, config.database)
            .option(ConnectionFactoryOptions.USER, config.user)
            .option(ConnectionFactoryOptions.PASSWORD, config.password)
            .build()

        ConnectionFactories.get(options)
    }

    /**
     * Cria uma nova conexão de forma assíncrona
     * Função suspend que dá a sensação de programação imperativa
     */
    suspend fun create(): Connection {
        return connectionFactory.create().awaitSingle()
    }

    /**
     * Obtém o ConnectionFactory subjacente para uso avançado
     */
    fun getUnderlyingConnectionFactory(): ConnectionFactory = connectionFactory
}
