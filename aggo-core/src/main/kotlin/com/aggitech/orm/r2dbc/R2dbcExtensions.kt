package com.aggitech.orm.r2dbc

import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.query.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Extensões para queries que adicionam suporte a operações reativas
 * Mantém a mesma DSL, mas adiciona métodos suspend
 */

/**
 * Executa uma query SELECT de forma assíncrona
 * Retorna todos os resultados de uma vez (coleta o Flow completo)
 *
 * Uso:
 * ```kotlin
 * val users = selectQuery.execute(connection, SqlDialect.POSTGRESQL)
 * ```
 */
suspend fun <T : Any> SelectQuery<T>.execute(
    connection: io.r2dbc.spi.Connection,
    dialect: SqlDialect
): List<Map<String, Any?>> {
    val executor = R2dbcQueryExecutor(connection, dialect)
    return executor.executeQuery(this)
}

/**
 * Executa uma query SELECT retornando um Flow
 * Streaming de resultados para processamento sob demanda
 *
 * Uso:
 * ```kotlin
 * selectQuery.executeAsFlow(connection, SqlDialect.POSTGRESQL)
 *     .collect { user ->
 *         println(user)
 *     }
 * ```
 */
fun <T : Any> SelectQuery<T>.executeAsFlow(
    connection: io.r2dbc.spi.Connection,
    dialect: SqlDialect
): Flow<Map<String, Any?>> {
    val executor = R2dbcQueryExecutor(connection, dialect)
    return executor.executeQueryAsFlow(this)
}

/**
 * Executa uma query INSERT de forma assíncrona
 * Retorna o número de linhas afetadas
 */
suspend fun <T : Any> InsertQuery<T>.execute(
    connection: io.r2dbc.spi.Connection,
    dialect: SqlDialect
): Long {
    val executor = R2dbcQueryExecutor(connection, dialect)
    return executor.executeInsert(this)
}

/**
 * Executa uma query INSERT e retorna as chaves geradas
 */
suspend fun <T : Any> InsertQuery<T>.executeReturningKeys(
    connection: io.r2dbc.spi.Connection,
    dialect: SqlDialect
): List<Long> {
    val executor = R2dbcQueryExecutor(connection, dialect)
    return executor.executeInsertReturningKeys(this)
}

/**
 * Executa uma query UPDATE de forma assíncrona
 * Retorna o número de linhas afetadas
 */
suspend fun <T : Any> UpdateQuery<T>.execute(
    connection: io.r2dbc.spi.Connection,
    dialect: SqlDialect
): Long {
    val executor = R2dbcQueryExecutor(connection, dialect)
    return executor.executeUpdate(this)
}

/**
 * Executa uma query DELETE de forma assíncrona
 * Retorna o número de linhas afetadas
 */
suspend fun <T : Any> DeleteQuery<T>.execute(
    connection: io.r2dbc.spi.Connection,
    dialect: SqlDialect
): Long {
    val executor = R2dbcQueryExecutor(connection, dialect)
    return executor.executeDelete(this)
}
