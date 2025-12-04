package com.aggitech.orm.r2dbc

import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.query.model.*
import com.aggitech.orm.sql.context.RenderedSql
import com.aggitech.orm.sql.renderer.*
import io.r2dbc.spi.Connection
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle

/**
 * Executor de queries R2DBC usando Coroutines
 *
 * API com suspend functions que dá a sensação de programação imperativa,
 * mas por baixo usa programação reativa com R2DBC
 *
 * Uso:
 * ```kotlin
 * val executor = R2dbcQueryExecutor(connection, SqlDialect.POSTGRESQL)
 *
 * // Parece imperativo, mas é reativo
 * val users = executor.executeQuery(selectQuery) // suspend - aguarda resultado
 * users.forEach { println(it) }
 * ```
 */
class R2dbcQueryExecutor(
    private val connection: Connection,
    private val dialect: SqlDialect
) {
    /**
     * Executa uma query SELECT e retorna os resultados
     * Suspend function - aguarda todos os resultados antes de retornar
     */
    suspend fun <T : Any> executeQuery(query: SelectQuery<T>): List<Map<String, Any?>> {
        val renderer = SelectRenderer(dialect)
        val rendered = renderer.render(query)

        return executeRenderedQuery(rendered)
    }

    /**
     * Executa uma query SELECT retornando um Flow para streaming
     * Útil para grandes volumes de dados
     */
    fun <T : Any> executeQueryAsFlow(query: SelectQuery<T>): Flow<Map<String, Any?>> {
        val renderer = SelectRenderer(dialect)
        val rendered = renderer.render(query)

        return executeRenderedQueryAsFlow(rendered)
    }

    /**
     * Executa uma query INSERT e retorna o número de linhas afetadas
     */
    suspend fun <T : Any> executeInsert(query: InsertQuery<T>): Long {
        val renderer = InsertRenderer(dialect)
        val rendered = renderer.render(query)

        return executeUpdate(rendered)
    }

    /**
     * Executa uma query INSERT e retorna as chaves geradas
     */
    suspend fun <T : Any> executeInsertReturningKeys(query: InsertQuery<T>): List<Long> {
        val renderer = InsertRenderer(dialect)
        val rendered = renderer.render(query)

        val statement = connection.createStatement(rendered.sql)
        bindParameters(statement, rendered.parameters)

        return statement.returnGeneratedValues()
            .execute()
            .asFlow()
            .collectToList { result ->
                result.map { row, _ ->
                    row.get(0, java.lang.Long::class.java)?.toLong() ?: 0L
                }.awaitSingle()
            }
    }

    /**
     * Executa uma query UPDATE e retorna o número de linhas afetadas
     */
    suspend fun <T : Any> executeUpdate(query: UpdateQuery<T>): Long {
        val renderer = UpdateRenderer(dialect)
        val rendered = renderer.render(query)

        return executeUpdate(rendered)
    }

    /**
     * Executa uma query DELETE e retorna o número de linhas afetadas
     */
    suspend fun <T : Any> executeDelete(query: DeleteQuery<T>): Long {
        val renderer = DeleteRenderer(dialect)
        val rendered = renderer.render(query)

        return executeUpdate(rendered)
    }

    /**
     * Executa SQL renderizado que retorna resultados (SELECT)
     * Coleta todos os resultados em uma lista
     */
    private suspend fun executeRenderedQuery(rendered: RenderedSql): List<Map<String, Any?>> {
        val statement = connection.createStatement(rendered.sql)
        bindParameters(statement, rendered.parameters)

        return statement.execute()
            .asFlow()
            .collectToList { result ->
                result.map { row, metadata ->
                    val map = mutableMapOf<String, Any?>()
                    metadata.columnMetadatas.forEach { columnMetadata ->
                        val columnName = columnMetadata.name
                        val value = row.get(columnName)
                        map[columnName] = value
                    }
                    map
                }.awaitSingle()
            }
    }

    /**
     * Executa SQL renderizado retornando um Flow
     * Streaming de resultados sem carregar tudo na memória
     */
    private fun executeRenderedQueryAsFlow(rendered: RenderedSql): Flow<Map<String, Any?>> = flow {
        val statement = connection.createStatement(rendered.sql)
        bindParameters(statement, rendered.parameters)

        statement.execute()
            .asFlow()
            .collect { result ->
                result.map { row, metadata ->
                    val map = mutableMapOf<String, Any?>()
                    metadata.columnMetadatas.forEach { columnMetadata ->
                        val columnName = columnMetadata.name
                        val value = row.get(columnName)
                        map[columnName] = value
                    }
                    map
                }.asFlow()
                    .collect { emit(it) }
            }
    }

    /**
     * Executa SQL renderizado que modifica dados (INSERT, UPDATE, DELETE)
     */
    private suspend fun executeUpdate(rendered: RenderedSql): Long {
        val statement = connection.createStatement(rendered.sql)
        bindParameters(statement, rendered.parameters)

        return statement.execute()
            .asFlow()
            .collectToList { result ->
                result.rowsUpdated.awaitSingle()
            }
            .sum()
    }

    /**
     * Bind de parâmetros no Statement R2DBC
     */
    private fun bindParameters(statement: Statement, parameters: List<Any?>) {
        parameters.forEachIndexed { index, value ->
            if (value == null) {
                statement.bindNull(index, Any::class.java)
            } else {
                statement.bind(index, value)
            }
        }
    }
}

/**
 * Extensão auxiliar para coletar Flow de Results em uma lista
 */
private suspend fun <T> Flow<Result>.collectToList(mapper: suspend (Result) -> T): List<T> {
    val list = mutableListOf<T>()
    collect { result ->
        list.add(mapper(result))
    }
    return list
}
