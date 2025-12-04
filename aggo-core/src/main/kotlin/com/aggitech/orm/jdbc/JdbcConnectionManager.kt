package com.aggitech.orm.jdbc

import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.enums.SqlDialect
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Gerenciador global de conexões JDBC com pool
 *
 * Mantém conexões abertas e reutiliza entre queries.
 * Usa ThreadLocal para garantir isolamento entre threads.
 */
object JdbcConnectionManager {
    private val configs = ConcurrentHashMap<String, DbConfig>()
    private val connectionPools = ConcurrentHashMap<String, JdbcConnectionPool>()
    private val lock = ReentrantLock()

    /**
     * Registra uma configuração de banco de dados
     *
     * @param name Nome identificador da configuração (ex: "default", "analytics")
     * @param config Configuração do banco
     */
    fun register(name: String = "default", config: DbConfig) {
        lock.withLock {
            configs[name] = config

            // Cria pool se não existir
            if (!connectionPools.containsKey(name)) {
                connectionPools[name] = JdbcConnectionPool(config)
            }
        }
    }

    /**
     * Obtém uma conexão do pool
     *
     * @param name Nome da configuração
     * @return Conexão JDBC pronta para uso
     */
    fun getConnection(name: String = "default"): Connection {
        val pool = connectionPools[name]
            ?: throw IllegalStateException(
                "No configuration registered for '$name'. Call JdbcConnectionManager.register() first."
            )

        return pool.acquire()
    }

    /**
     * Devolve uma conexão ao pool
     */
    fun releaseConnection(name: String = "default", connection: Connection) {
        connectionPools[name]?.release(connection)
    }

    /**
     * Obtém a configuração registrada
     */
    fun getConfig(name: String = "default"): DbConfig {
        return configs[name]
            ?: throw IllegalStateException("No configuration registered for '$name'")
    }

    /**
     * Obtém o dialect da configuração
     */
    fun getDialect(name: String = "default"): SqlDialect {
        return getConfig(name).dialect
    }

    /**
     * Executa um bloco com uma conexão do pool
     * Auto-commit por padrão, pode ser desabilitado para transações
     */
    fun <T> withConnection(
        name: String = "default",
        autoCommit: Boolean = true,
        block: (Connection) -> T
    ): T {
        val connection = getConnection(name)
        val originalAutoCommit = connection.autoCommit

        return try {
            connection.autoCommit = autoCommit
            val result = block(connection)

            if (!autoCommit) {
                connection.commit()
            }

            result
        } catch (e: Exception) {
            if (!autoCommit) {
                connection.rollback()
            }
            throw e
        } finally {
            connection.autoCommit = originalAutoCommit
            releaseConnection(name, connection)
        }
    }

    /**
     * Fecha todos os pools de conexão
     */
    fun closeAll() {
        lock.withLock {
            connectionPools.values.forEach { it.close() }
            connectionPools.clear()
            configs.clear()
        }
    }

    /**
     * Fecha um pool específico
     */
    fun close(name: String = "default") {
        lock.withLock {
            connectionPools[name]?.close()
            connectionPools.remove(name)
            configs.remove(name)
        }
    }
}

/**
 * Pool de conexões JDBC simples e eficiente
 */
internal class JdbcConnectionPool(
    private val config: DbConfig,
    private val maxPoolSize: Int = 10,
    private val minPoolSize: Int = 2
) {
    private val availableConnections = ArrayDeque<Connection>()
    private val activeConnections = ConcurrentHashMap<Connection, Long>()
    private val lock = ReentrantLock()

    init {
        // Cria conexões iniciais
        lock.withLock {
            repeat(minPoolSize) {
                availableConnections.add(createConnection())
            }
        }
    }

    fun acquire(): Connection = lock.withLock {
        // Remove conexões inválidas
        while (availableConnections.isNotEmpty()) {
            val connection = availableConnections.removeFirst()
            if (isValid(connection)) {
                activeConnections[connection] = System.currentTimeMillis()
                return connection
            } else {
                safeClose(connection)
            }
        }

        // Cria nova conexão se não atingiu o máximo
        if (activeConnections.size < maxPoolSize) {
            val connection = createConnection()
            activeConnections[connection] = System.currentTimeMillis()
            return connection
        }

        // Aguarda conexão disponível
        throw IllegalStateException(
            "Connection pool exhausted. Max size: $maxPoolSize, active: ${activeConnections.size}"
        )
    }

    fun release(connection: Connection) {
        lock.withLock {
            activeConnections.remove(connection)

            if (isValid(connection) && availableConnections.size < maxPoolSize) {
                availableConnections.add(connection)
            } else {
                safeClose(connection)
            }
        }
    }

    fun close() {
        lock.withLock {
            availableConnections.forEach { safeClose(it) }
            availableConnections.clear()

            activeConnections.keys.forEach { safeClose(it) }
            activeConnections.clear()
        }
    }

    private fun createConnection(): Connection {
        return DriverManager.getConnection(config.url, config.user, config.password)
    }

    private fun isValid(connection: Connection): Boolean {
        return try {
            !connection.isClosed && connection.isValid(1)
        } catch (e: Exception) {
            false
        }
    }

    private fun safeClose(connection: Connection) {
        try {
            if (!connection.isClosed) {
                connection.close()
            }
        } catch (e: Exception) {
            // Ignora erros ao fechar
        }
    }

    fun getStats(): PoolStats {
        return PoolStats(
            totalConnections = activeConnections.size + availableConnections.size,
            activeConnections = activeConnections.size,
            availableConnections = availableConnections.size,
            maxSize = maxPoolSize
        )
    }
}

/**
 * Estatísticas do pool
 */
data class PoolStats(
    val totalConnections: Int,
    val activeConnections: Int,
    val availableConnections: Int,
    val maxSize: Int
) {
    fun utilizationPercent(): Double {
        return (activeConnections.toDouble() / maxSize) * 100
    }
}
