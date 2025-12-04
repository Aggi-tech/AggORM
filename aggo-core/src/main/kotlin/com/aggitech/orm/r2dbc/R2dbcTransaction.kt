package com.aggitech.orm.r2dbc

import io.r2dbc.spi.Connection
import kotlinx.coroutines.reactive.awaitSingle

/**
 * Gerenciamento de transações R2DBC com Coroutines
 *
 * Uso similar ao try-catch tradicional, mas com suspend functions:
 * ```kotlin
 * transaction(connection) {
 *     // Operações dentro da transação
 *     insert<User> { ... }.execute(connection, dialect)
 *     update<Order> { ... }.execute(connection, dialect)
 * } // Commit automático se bem-sucedido, rollback em caso de exceção
 * ```
 */

/**
 * Executa um bloco de código dentro de uma transação
 *
 * @param connection Conexão R2DBC
 * @param block Bloco de código a ser executado na transação
 * @return Resultado do bloco
 * @throws Exception Se houver erro, faz rollback e propaga a exceção
 */
suspend fun <T> transaction(
    connection: Connection,
    block: suspend () -> T
): T {
    // Inicia a transação
    connection.beginTransaction().awaitSingle()

    return try {
        // Executa o bloco
        val result = block()

        // Commit se tudo correu bem
        connection.commitTransaction().awaitSingle()

        result
    } catch (e: Exception) {
        // Rollback em caso de erro
        try {
            connection.rollbackTransaction().awaitSingle()
        } catch (rollbackException: Exception) {
            // Adiciona exceção de rollback como suprimida
            e.addSuppressed(rollbackException)
        }

        // Propaga a exceção original
        throw e
    }
}

/**
 * Executa um bloco de código dentro de uma transação criando uma nova conexão
 *
 * @param connectionFactory Factory para criar conexão
 * @param block Bloco de código a ser executado na transação
 * @return Resultado do bloco
 */
suspend fun <T> transaction(
    connectionFactory: R2dbcConnectionFactory,
    block: suspend (Connection) -> T
): T {
    val connection = connectionFactory.create()

    return try {
        transaction(connection) {
            block(connection)
        }
    } finally {
        // Fecha a conexão
        connection.close().awaitSingle()
    }
}

/**
 * Classe para gerenciamento manual de transações (uso avançado)
 *
 * Uso:
 * ```kotlin
 * val tx = R2dbcTransaction(connection)
 * try {
 *     tx.begin()
 *     // operações
 *     tx.commit()
 * } catch (e: Exception) {
 *     tx.rollback()
 *     throw e
 * }
 * ```
 */
class R2dbcTransaction(private val connection: Connection) {

    private var active = false

    /**
     * Inicia uma transação
     */
    suspend fun begin() {
        if (active) {
            throw IllegalStateException("Transaction already active")
        }
        connection.beginTransaction().awaitSingle()
        active = true
    }

    /**
     * Faz commit da transação
     */
    suspend fun commit() {
        if (!active) {
            throw IllegalStateException("No active transaction")
        }
        connection.commitTransaction().awaitSingle()
        active = false
    }

    /**
     * Faz rollback da transação
     */
    suspend fun rollback() {
        if (!active) {
            throw IllegalStateException("No active transaction")
        }
        connection.rollbackTransaction().awaitSingle()
        active = false
    }

    /**
     * Verifica se há uma transação ativa
     */
    fun isActive(): Boolean = active
}
