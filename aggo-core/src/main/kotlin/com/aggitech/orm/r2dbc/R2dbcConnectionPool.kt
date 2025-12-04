package com.aggitech.orm.r2dbc

import com.aggitech.orm.config.R2dbcConfig
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pool de conexões R2DBC com gerenciamento via Coroutines
 *
 * Implementação leve e independente de frameworks que gerencia
 * um pool de conexões R2DBC usando Channel para controle de acesso.
 *
 * Uso básico:
 * ```kotlin
 * val pool = R2dbcConnectionPool(config)
 *
 * try {
 *     val connection = pool.acquire() // suspend - aguarda conexão disponível
 *     try {
 *         // usar conexão
 *     } finally {
 *         pool.release(connection)
 *     }
 * } finally {
 *     pool.close()
 * }
 * ```
 *
 * Uso com helper:
 * ```kotlin
 * pool.withConnection { connection ->
 *     // usar conexão - auto-released
 *     select<User> { ... }.execute(connection, dialect)
 * }
 * ```
 */
class R2dbcConnectionPool(
    private val config: R2dbcConfig,
    private val poolConfig: PoolConfig = PoolConfig()
) {
    private val connectionFactory: ConnectionFactory
    private val availableConnections: Channel<PooledConnection>
    private val activeConnections = ConcurrentHashMap<Connection, PooledConnection>()
    private val totalConnections = AtomicInteger(0)
    private val mutex = Mutex()
    private var closed = false

    init {
        // Cria ConnectionFactory
        val options = ConnectionFactoryOptions.builder()
            .option(ConnectionFactoryOptions.DRIVER, config.getDriver())
            .option(ConnectionFactoryOptions.HOST, config.host)
            .option(ConnectionFactoryOptions.PORT, config.getPort())
            .option(ConnectionFactoryOptions.DATABASE, config.database)
            .option(ConnectionFactoryOptions.USER, config.user)
            .option(ConnectionFactoryOptions.PASSWORD, config.password)
            .build()

        connectionFactory = ConnectionFactories.get(options)

        // Cria channel com capacidade do pool
        availableConnections = Channel(poolConfig.maxSize)
    }

    /**
     * Inicializa o pool criando conexões iniciais
     *
     * Deve ser chamado antes de usar o pool
     */
    suspend fun initialize() {
        repeat(poolConfig.initialSize) {
            createConnection()
        }
    }

    /**
     * Adquire uma conexão do pool
     *
     * Se nenhuma conexão estiver disponível:
     * - Se pool não está cheio: cria nova conexão
     * - Se pool está cheio: aguarda até uma ficar disponível (com timeout)
     *
     * @return Conexão pronta para uso
     * @throws R2dbcException.ConnectionException se não conseguir adquirir
     */
    suspend fun acquire(): Connection {
        if (closed) {
            throw R2dbcException.ConnectionException(
                "Connection pool is closed",
                null
            )
        }

        return try {
            withTimeout(poolConfig.maxAcquireTime.toMillis()) {
                acquireInternal()
            }
        } catch (e: Exception) {
            throw R2dbcException.ConnectionException(
                "Failed to acquire connection within ${poolConfig.maxAcquireTime}",
                e
            )
        }
    }

    private suspend fun acquireInternal(): Connection {
        // Tenta pegar conexão disponível
        val pooledConn = availableConnections.tryReceive().getOrNull()

        if (pooledConn != null) {
            // Valida conexão
            if (isConnectionValid(pooledConn)) {
                pooledConn.lastUsed = System.currentTimeMillis()
                activeConnections[pooledConn.connection] = pooledConn
                return pooledConn.connection
            } else {
                // Conexão inválida, descarta e cria nova
                closeConnection(pooledConn)
                totalConnections.decrementAndGet()
            }
        }

        // Cria nova conexão se não atingiu o máximo
        if (totalConnections.get() < poolConfig.maxSize) {
            return createConnection().connection
        }

        // Aguarda conexão disponível
        val waitingConn = availableConnections.receive()
        if (isConnectionValid(waitingConn)) {
            waitingConn.lastUsed = System.currentTimeMillis()
            activeConnections[waitingConn.connection] = waitingConn
            return waitingConn.connection
        } else {
            closeConnection(waitingConn)
            totalConnections.decrementAndGet()
            return acquireInternal() // Tenta novamente
        }
    }

    /**
     * Devolve uma conexão ao pool
     *
     * @param connection Conexão a ser devolvida
     */
    suspend fun release(connection: Connection) {
        val pooledConn = activeConnections.remove(connection)

        if (pooledConn != null) {
            pooledConn.lastUsed = System.currentTimeMillis()

            if (isConnectionValid(pooledConn)) {
                availableConnections.send(pooledConn)
            } else {
                closeConnection(pooledConn)
                totalConnections.decrementAndGet()
            }
        }
    }

    /**
     * Executa bloco com conexão e auto-release
     *
     * Uso:
     * ```kotlin
     * val result = pool.withConnection { connection ->
     *     select<User> { ... }.execute(connection, dialect)
     * }
     * ```
     */
    suspend fun <T> withConnection(block: suspend (Connection) -> T): T {
        val connection = acquire()
        return try {
            block(connection)
        } finally {
            release(connection)
        }
    }

    /**
     * Cria uma nova conexão e adiciona ao pool
     */
    private suspend fun createConnection(): PooledConnection = mutex.withLock {
        val connection = connectionFactory.create().awaitSingle()
        val pooledConn = PooledConnection(
            connection = connection,
            createdAt = System.currentTimeMillis(),
            lastUsed = System.currentTimeMillis()
        )

        totalConnections.incrementAndGet()
        activeConnections[connection] = pooledConn

        return pooledConn
    }

    /**
     * Valida se conexão ainda é utilizável
     */
    private suspend fun isConnectionValid(pooledConn: PooledConnection): Boolean {
        val now = System.currentTimeMillis()
        val idleTime = now - pooledConn.lastUsed

        // Verifica idle time
        if (idleTime > poolConfig.maxIdleTime.toMillis()) {
            return false
        }

        // Verifica se conexão ainda está aberta
        return try {
            !pooledConn.connection.isAutoCommit // Acesso simples para testar conexão
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fecha uma conexão específica
     */
    private suspend fun closeConnection(pooledConn: PooledConnection) {
        try {
            pooledConn.connection.close().awaitSingle()
        } catch (e: Exception) {
            // Ignora erros ao fechar
        }
    }

    /**
     * Fecha o pool e todas as conexões
     */
    suspend fun close() {
        if (closed) return

        mutex.withLock {
            closed = true

            // Fecha conexões disponíveis
            availableConnections.close()
            for (pooledConn in availableConnections) {
                closeConnection(pooledConn)
            }

            // Fecha conexões ativas
            for ((_, pooledConn) in activeConnections) {
                closeConnection(pooledConn)
            }

            activeConnections.clear()
            totalConnections.set(0)
        }
    }

    /**
     * Retorna estatísticas do pool
     */
    fun getStats(): PoolStats {
        return PoolStats(
            totalConnections = totalConnections.get(),
            activeConnections = activeConnections.size,
            availableConnections = availableConnections.isEmpty.let {
                poolConfig.maxSize - activeConnections.size
            },
            maxSize = poolConfig.maxSize
        )
    }

    /**
     * Representa uma conexão gerenciada pelo pool
     */
    private data class PooledConnection(
        val connection: Connection,
        val createdAt: Long,
        var lastUsed: Long
    )
}

/**
 * Configuração do pool de conexões
 */
data class PoolConfig(
    /**
     * Número inicial de conexões ao criar o pool
     * Padrão: 10
     */
    val initialSize: Int = 10,

    /**
     * Número máximo de conexões no pool
     * Padrão: 20
     */
    val maxSize: Int = 20,

    /**
     * Tempo máximo que uma conexão pode ficar idle antes de ser fechada
     * Padrão: 30 segundos
     */
    val maxIdleTime: Duration = Duration.ofSeconds(30),

    /**
     * Tempo máximo para aguardar uma conexão disponível
     * Padrão: 3 segundos
     */
    val maxAcquireTime: Duration = Duration.ofSeconds(3)
) {
    companion object {
        /**
         * Configuração para alta concorrência
         */
        fun highConcurrency(): PoolConfig = PoolConfig(
            initialSize = 20,
            maxSize = 50,
            maxIdleTime = Duration.ofSeconds(60),
            maxAcquireTime = Duration.ofSeconds(5)
        )

        /**
         * Configuração para baixa concorrência (economia de recursos)
         */
        fun lowConcurrency(): PoolConfig = PoolConfig(
            initialSize = 5,
            maxSize = 10,
            maxIdleTime = Duration.ofSeconds(15),
            maxAcquireTime = Duration.ofSeconds(2)
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
