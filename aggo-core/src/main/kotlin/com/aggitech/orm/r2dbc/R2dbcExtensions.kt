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

// ==================== Extensões Ergonômicas usando R2dbcConnectionManager ====================

/**
 * Executa uma query SELECT usando o connection manager
 *
 * Uso simplificado:
 * ```kotlin
 * // Registra configuração uma vez no início da aplicação
 * R2dbcConnectionManager.register(config = myR2dbcConfig)
 *
 * // Usa em qualquer lugar
 * val users = select<User> {
 *     where { User::age gte 18 }
 * }.execute()
 * ```
 *
 * @param configName Nome da configuração registrada (padrão: "default")
 * @return Lista de resultados mapeados como Map<String, Any?>
 */
suspend fun <T : Any> SelectQuery<T>.execute(configName: String = "default"): List<Map<String, Any?>> {
    return R2dbcConnectionManager.withConnection(configName) { connection ->
        val dialect = R2dbcConnectionManager.getDialect(configName)
        execute(connection, dialect)
    }
}

/**
 * Executa uma query SELECT retornando Flow usando o connection manager
 *
 * Uso:
 * ```kotlin
 * select<User> { ... }.executeAsFlow()
 *     .collect { user -> println(user) }
 * ```
 *
 * @param configName Nome da configuração registrada
 * @return Flow de resultados para streaming
 */
suspend fun <T : Any> SelectQuery<T>.executeAsFlow(configName: String = "default"): Flow<Map<String, Any?>> {
    val pool = R2dbcConnectionManager.getPool(configName)
    val dialect = R2dbcConnectionManager.getDialect(configName)

    return kotlinx.coroutines.flow.flow {
        pool.withConnection { connection ->
            executeAsFlow(connection, dialect).collect { emit(it) }
        }
    }
}

/**
 * Executa uma query INSERT usando o connection manager
 *
 * @param configName Nome da configuração registrada
 * @return Número de linhas afetadas
 */
suspend fun <T : Any> InsertQuery<T>.execute(configName: String = "default"): Long {
    return R2dbcConnectionManager.withConnection(configName) { connection ->
        val dialect = R2dbcConnectionManager.getDialect(configName)
        execute(connection, dialect)
    }
}

/**
 * Executa uma query INSERT e retorna as chaves geradas usando o connection manager
 *
 * @param configName Nome da configuração registrada
 * @return Lista de IDs gerados
 */
suspend fun <T : Any> InsertQuery<T>.executeReturningKeys(configName: String = "default"): List<Long> {
    return R2dbcConnectionManager.withConnection(configName) { connection ->
        val dialect = R2dbcConnectionManager.getDialect(configName)
        executeReturningKeys(connection, dialect)
    }
}

/**
 * Executa uma query UPDATE usando o connection manager
 *
 * @param configName Nome da configuração registrada
 * @return Número de linhas afetadas
 */
suspend fun <T : Any> UpdateQuery<T>.execute(configName: String = "default"): Long {
    return R2dbcConnectionManager.withConnection(configName) { connection ->
        val dialect = R2dbcConnectionManager.getDialect(configName)
        execute(connection, dialect)
    }
}

/**
 * Executa uma query DELETE usando o connection manager
 *
 * @param configName Nome da configuração registrada
 * @return Número de linhas afetadas
 */
suspend fun <T : Any> DeleteQuery<T>.execute(configName: String = "default"): Long {
    return R2dbcConnectionManager.withConnection(configName) { connection ->
        val dialect = R2dbcConnectionManager.getDialect(configName)
        execute(connection, dialect)
    }
}

/**
 * Executa múltiplas queries reativas em uma transação
 *
 * Uso:
 * ```kotlin
 * transaction {
 *     insert<User> { ... }.execute()
 *     insert<Order> { ... }.execute()
 *     // Commit automático ao sair do bloco
 *     // Rollback automático em caso de exceção
 * }
 * ```
 */
suspend fun <T> transaction(configName: String = "default", block: suspend () -> T): T {
    val connection = R2dbcConnectionManager.getPool(configName).acquire()

    return try {
        transaction(connection) {
            block()
        }
    } finally {
        R2dbcConnectionManager.getPool(configName).release(connection)
    }
}

// ==================== Extensões com Mapeamento Automático ====================

/**
 * Executa uma query SELECT e mapeia para entidades (reativo)
 *
 * Uso:
 * ```kotlin
 * val users: List<User> = select<User> {
 *     where { User::age gte 18 }
 * }.executeAsEntities()
 * ```
 */
suspend inline fun <reified T : Any> SelectQuery<T>.executeAsEntities(
    configName: String = "default"
): List<T> {
    val results = execute(configName)
    return com.aggitech.orm.mapping.EntityMapper.mapList(results, T::class)
}

/**
 * Executa uma query SELECT e retorna a primeira entidade ou null (reativo)
 *
 * Uso:
 * ```kotlin
 * val user: User? = select<User> {
 *     where { User::id eq 1L }
 * }.executeAsEntityOrNull()
 * ```
 */
suspend inline fun <reified T : Any> SelectQuery<T>.executeAsEntityOrNull(
    configName: String = "default"
): T? {
    val results = execute(configName)
    return results.firstOrNull()?.let { com.aggitech.orm.mapping.EntityMapper.map(it, T::class) }
}

/**
 * Executa uma query SELECT e retorna a primeira entidade ou lança exceção (reativo)
 *
 * @throws NoSuchElementException se não houver resultado
 */
suspend inline fun <reified T : Any> SelectQuery<T>.executeAsEntity(
    configName: String = "default"
): T {
    return executeAsEntityOrNull(configName)
        ?: throw NoSuchElementException("No entity found for query")
}

/**
 * Executa uma query SELECT como Flow de entidades
 *
 * Uso:
 * ```kotlin
 * select<User> { ... }.executeAsEntityFlow()
 *     .collect { user: User ->
 *         println(user.name)
 *     }
 * ```
 */
suspend inline fun <reified T : Any> SelectQuery<T>.executeAsEntityFlow(
    configName: String = "default"
): kotlinx.coroutines.flow.Flow<T> {
    return kotlinx.coroutines.flow.flow {
        executeAsFlow(configName).collect { row ->
            val entity = com.aggitech.orm.mapping.EntityMapper.map(row, T::class)
            emit(entity)
        }
    }
}
