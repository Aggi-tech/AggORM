package com.aggitech.orm.r2dbc

import com.aggitech.orm.config.R2dbcConfig
import com.aggitech.orm.enums.SqlDialect
import io.r2dbc.spi.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Gerenciador global de conexões R2DBC com pool
 *
 * Mantém pools de conexões R2DBC e reutiliza entre queries.
 * Gerencia configurações globalmente para acesso simplificado.
 */
object R2dbcConnectionManager {
    private val configs = ConcurrentHashMap<String, R2dbcConfig>()
    private val connectionPools = ConcurrentHashMap<String, R2dbcConnectionPool>()
    private val lock = ReentrantLock()

    /**
     * Registra uma configuração de banco de dados R2DBC
     *
     * @param name Nome identificador da configuração (ex: "default", "analytics")
     * @param config Configuração do banco
     * @param poolConfig Configuração do pool (opcional)
     */
    suspend fun register(
        name: String = "default",
        config: R2dbcConfig,
        poolConfig: PoolConfig = PoolConfig()
    ) {
        // Cria pool fora do lock para evitar suspension point em critical section
        val newPool = if (!connectionPools.containsKey(name)) {
            R2dbcConnectionPool(config, poolConfig)
        } else {
            null
        }

        lock.withLock {
            configs[name] = config

            // Adiciona pool se foi criado
            if (newPool != null && !connectionPools.containsKey(name)) {
                connectionPools[name] = newPool
            }
        }

        // Inicializa pool fora do lock
        newPool?.initialize()
    }

    /**
     * Obtém o pool de conexões
     *
     * @param name Nome da configuração
     * @return Pool de conexões R2DBC
     */
    fun getPool(name: String = "default"): R2dbcConnectionPool {
        return connectionPools[name]
            ?: throw IllegalStateException(
                "No R2DBC configuration registered for '$name'. Call R2dbcConnectionManager.register() first."
            )
    }

    /**
     * Obtém a configuração registrada
     */
    fun getConfig(name: String = "default"): R2dbcConfig {
        return configs[name]
            ?: throw IllegalStateException("No R2DBC configuration registered for '$name'")
    }

    /**
     * Obtém o dialect da configuração
     */
    fun getDialect(name: String = "default"): SqlDialect {
        return getConfig(name).dialect
    }

    /**
     * Executa um bloco com uma conexão do pool
     *
     * Uso:
     * ```kotlin
     * R2dbcConnectionManager.withConnection { connection ->
     *     select<User> { ... }.execute(connection, dialect)
     * }
     * ```
     */
    suspend fun <T> withConnection(
        name: String = "default",
        block: suspend (Connection) -> T
    ): T {
        val pool = getPool(name)
        return pool.withConnection(block)
    }

    /**
     * Fecha todos os pools de conexão
     */
    suspend fun closeAll() {
        // Coleta pools para fechar fora do lock
        val poolsToClose = lock.withLock {
            val pools = connectionPools.values.toList()
            connectionPools.clear()
            configs.clear()
            pools
        }

        // Fecha pools fora do lock para evitar suspension point em critical section
        poolsToClose.forEach { it.close() }
    }

    /**
     * Fecha um pool específico
     */
    suspend fun close(name: String = "default") {
        // Remove pool do map dentro do lock
        val poolToClose = lock.withLock {
            val pool = connectionPools[name]
            connectionPools.remove(name)
            configs.remove(name)
            pool
        }

        // Fecha pool fora do lock para evitar suspension point em critical section
        poolToClose?.close()
    }

    /**
     * Obtém estatísticas de um pool
     */
    fun getStats(name: String = "default"): PoolStats {
        return getPool(name).getStats()
    }
}
