package com.aggitech.orm.r2dbc

import com.aggitech.orm.exceptions.OrmException

/**
 * Hierarquia de exceções específicas para operações R2DBC
 *
 * Todas as exceções R2DBC herdam de OrmException, permitindo
 * tratamento unificado de erros do ORM.
 */
sealed class R2dbcException(
    message: String,
    cause: Throwable? = null
) : OrmException(message, cause) {

    /**
     * Erro ao estabelecer ou gerenciar conexão R2DBC
     *
     * Exemplos:
     * - Falha ao conectar no banco
     * - Timeout de conexão
     * - Pool esgotado
     * - Conexão perdida durante operação
     *
     * Uso:
     * ```kotlin
     * try {
     *     val connection = factory.create()
     * } catch (e: R2dbcException.ConnectionException) {
     *     logger.error("Falha na conexão: ${e.message}")
     *     // Tentar reconectar ou usar fallback
     * }
     * ```
     */
    class ConnectionException(
        message: String,
        cause: Throwable? = null
    ) : R2dbcException(message, cause) {

        companion object {
            fun timeout(timeoutMillis: Long, cause: Throwable? = null): ConnectionException {
                return ConnectionException(
                    "Connection timeout after ${timeoutMillis}ms",
                    cause
                )
            }

            fun poolExhausted(maxSize: Int): ConnectionException {
                return ConnectionException(
                    "Connection pool exhausted (max size: $maxSize)"
                )
            }

            fun connectionLost(cause: Throwable? = null): ConnectionException {
                return ConnectionException(
                    "Connection lost during operation",
                    cause
                )
            }
        }
    }

    /**
     * Erro durante streaming de dados
     *
     * Exemplos:
     * - Erro ao processar stream de resultados
     * - Cliente desconectou durante streaming
     * - Erro de serialização no stream
     * - Buffer overflow
     *
     * Uso:
     * ```kotlin
     * try {
     *     query.executeAsFlow(connection, dialect)
     *         .collect { row -> process(row) }
     * } catch (e: R2dbcException.StreamingException) {
     *     logger.warn("Stream interrompido: ${e.message}")
     *     // Cleanup ou retry
     * }
     * ```
     */
    class StreamingException(
        message: String,
        cause: Throwable? = null
    ) : R2dbcException(message, cause) {

        companion object {
            fun clientDisconnected(): StreamingException {
                return StreamingException(
                    "Client disconnected during streaming"
                )
            }

            fun serializationError(cause: Throwable): StreamingException {
                return StreamingException(
                    "Error serializing stream data",
                    cause
                )
            }

            fun processingError(rowIndex: Long, cause: Throwable): StreamingException {
                return StreamingException(
                    "Error processing row $rowIndex in stream",
                    cause
                )
            }
        }
    }

    /**
     * Erro relacionado a backpressure no streaming
     *
     * Ocorre quando o producer está enviando dados mais rápido
     * do que o consumer consegue processar.
     *
     * Exemplos:
     * - Buffer cheio
     * - Consumer lento demais
     * - Necessidade de aplicar rate limiting
     *
     * Uso:
     * ```kotlin
     * try {
     *     query.executeAsFlow(connection, dialect)
     *         .buffer(256) // Controla backpressure
     *         .collect { row -> slowProcess(row) }
     * } catch (e: R2dbcException.BackpressureException) {
     *     logger.warn("Backpressure detectado: ${e.message}")
     *     // Aumentar buffer ou otimizar consumer
     * }
     * ```
     */
    class BackpressureException(
        message: String,
        cause: Throwable? = null
    ) : R2dbcException(message, cause) {

        companion object {
            fun bufferOverflow(bufferSize: Int): BackpressureException {
                return BackpressureException(
                    "Buffer overflow (size: $bufferSize). Consumer is too slow."
                )
            }

            fun slowConsumer(lag: Long): BackpressureException {
                return BackpressureException(
                    "Consumer lag detected: ${lag}ms behind producer"
                )
            }
        }
    }

    /**
     * Erro durante transação R2DBC
     *
     * Exemplos:
     * - Falha no commit
     * - Falha no rollback
     * - Transação já encerrada
     * - Deadlock
     *
     * Uso:
     * ```kotlin
     * try {
     *     transaction(connection) {
     *         insert<User> { ... }.execute(connection, dialect)
     *         update<Order> { ... }.execute(connection, dialect)
     *     }
     * } catch (e: R2dbcException.TransactionException) {
     *     logger.error("Transação falhou: ${e.message}")
     *     // Não precisa fazer rollback manual - já foi feito
     * }
     * ```
     */
    class TransactionException(
        message: String,
        cause: Throwable? = null
    ) : R2dbcException(message, cause) {

        companion object {
            fun commitFailed(cause: Throwable): TransactionException {
                return TransactionException(
                    "Failed to commit transaction",
                    cause
                )
            }

            fun rollbackFailed(cause: Throwable): TransactionException {
                return TransactionException(
                    "Failed to rollback transaction",
                    cause
                )
            }

            fun alreadyCompleted(): TransactionException {
                return TransactionException(
                    "Transaction already completed (committed or rolled back)"
                )
            }

            fun deadlock(cause: Throwable): TransactionException {
                return TransactionException(
                    "Transaction deadlock detected",
                    cause
                )
            }
        }
    }

    /**
     * Erro ao executar statement R2DBC
     *
     * Exemplos:
     * - SQL inválido
     * - Constraint violation
     * - Type mismatch
     * - Statement timeout
     *
     * Uso:
     * ```kotlin
     * try {
     *     insert<User> {
     *         User::email to "duplicate@email.com"
     *     }.execute(connection, dialect)
     * } catch (e: R2dbcException.StatementException) {
     *     if (e.isConstraintViolation()) {
     *         // Tratar duplicata
     *     }
     * }
     * ```
     */
    class StatementException(
        message: String,
        cause: Throwable? = null,
        val sqlState: String? = null
    ) : R2dbcException(message, cause) {

        fun isConstraintViolation(): Boolean {
            return sqlState?.startsWith("23") == true
        }

        fun isUniqueViolation(): Boolean {
            return sqlState == "23505"
        }

        fun isForeignKeyViolation(): Boolean {
            return sqlState == "23503"
        }

        companion object {
            fun invalidSql(sql: String, cause: Throwable): StatementException {
                return StatementException(
                    "Invalid SQL statement: $sql",
                    cause
                )
            }

            fun executionTimeout(timeoutMillis: Long): StatementException {
                return StatementException(
                    "Statement execution timeout after ${timeoutMillis}ms"
                )
            }

            fun parameterBindingError(paramIndex: Int, cause: Throwable): StatementException {
                return StatementException(
                    "Error binding parameter at index $paramIndex",
                    cause
                )
            }
        }
    }

    /**
     * Erro específico de operações SSE
     *
     * Exemplos:
     * - Erro ao formatar evento SSE
     * - Cliente não suporta SSE
     * - Heartbeat falhou
     * - Event ID inválido
     *
     * Uso:
     * ```kotlin
     * try {
     *     query.toSSEWithHeartbeat(connection, dialect)
     *         .collect { event -> sendToClient(event) }
     * } catch (e: R2dbcException.SseException) {
     *     logger.error("SSE error: ${e.message}")
     *     // Fechar stream e notificar cliente
     * }
     * ```
     */
    class SseException(
        message: String,
        cause: Throwable? = null
    ) : R2dbcException(message, cause) {

        companion object {
            fun formattingError(eventId: String?, cause: Throwable): SseException {
                return SseException(
                    "Error formatting SSE event${eventId?.let { " (id: $it)" } ?: ""}",
                    cause
                )
            }

            fun heartbeatFailed(cause: Throwable): SseException {
                return SseException(
                    "Failed to send SSE heartbeat",
                    cause
                )
            }

            fun invalidEventId(eventId: String): SseException {
                return SseException(
                    "Invalid SSE event ID: $eventId"
                )
            }

            fun clientNotSupported(): SseException {
                return SseException(
                    "Client does not support Server-Sent Events"
                )
            }
        }
    }
}

/**
 * Extensões para tratamento de exceções R2DBC
 */

/**
 * Executa bloco e converte exceções R2DBC genéricas
 */
inline fun <T> catchR2dbcErrors(block: () -> T): T {
    return try {
        block()
    } catch (e: R2dbcException) {
        throw e  // Já é R2dbcException, propaga
    } catch (e: Exception) {
        throw R2dbcException.StatementException(
            "Unexpected R2DBC error: ${e.message}",
            e
        )
    }
}

/**
 * Verifica se exceção é recuperável (pode fazer retry)
 */
fun R2dbcException.isRecoverable(): Boolean {
    return when (this) {
        is R2dbcException.ConnectionException -> true  // Pode reconectar
        is R2dbcException.BackpressureException -> true  // Pode esperar
        is R2dbcException.TransactionException -> false  // Não pode fazer retry
        is R2dbcException.StatementException -> false  // SQL error não é recuperável
        is R2dbcException.StreamingException -> this.message?.contains("disconnect") == true
        is R2dbcException.SseException -> this.message?.contains("heartbeat") == true
    }
}

/**
 * Extrai código de erro SQL se disponível
 */
fun R2dbcException.getSqlState(): String? {
    return when (this) {
        is R2dbcException.StatementException -> this.sqlState
        else -> null
    }
}
