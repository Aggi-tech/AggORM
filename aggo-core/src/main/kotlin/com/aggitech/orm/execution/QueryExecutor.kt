package com.aggitech.orm.execution

import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.query.model.*
import com.aggitech.orm.sql.context.RenderedSql
import com.aggitech.orm.sql.renderer.*
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Executor de queries SQL com suporte a PreparedStatements
 */
class QueryExecutor(
    private val connection: Connection,
    private val dialect: SqlDialect
) {
    /**
     * Executa uma query SELECT e retorna o ResultSet mapeado
     */
    fun <T : Any> executeQuery(query: SelectQuery<T>): List<Map<String, Any?>> {
        val renderer = SelectRenderer(dialect)
        val rendered = renderer.render(query)

        return executeRenderedQuery(rendered)
    }

    /**
     * Executa uma query INSERT e retorna o número de linhas afetadas
     */
    fun <T : Any> executeInsert(query: InsertQuery<T>): Int {
        val renderer = InsertRenderer(dialect)
        val rendered = renderer.render(query)

        return executeUpdate(rendered)
    }

    /**
     * Executa uma query UPDATE e retorna o número de linhas afetadas
     */
    fun <T : Any> executeUpdate(query: UpdateQuery<T>): Int {
        val renderer = UpdateRenderer(dialect)
        val rendered = renderer.render(query)

        return executeUpdate(rendered)
    }

    /**
     * Executa uma query DELETE e retorna o número de linhas afetadas
     */
    fun <T : Any> executeDelete(query: DeleteQuery<T>): Int {
        val renderer = DeleteRenderer(dialect)
        val rendered = renderer.render(query)

        return executeUpdate(rendered)
    }

    /**
     * Executa SQL renderizado que retorna resultados (SELECT)
     */
    private fun executeRenderedQuery(rendered: RenderedSql): List<Map<String, Any?>> {
        return connection.prepareStatement(rendered.sql).use { statement ->
            bindParameters(statement, rendered.parameters)

            statement.executeQuery().use { resultSet ->
                mapResultSet(resultSet)
            }
        }
    }

    /**
     * Executa SQL renderizado que modifica dados (INSERT, UPDATE, DELETE)
     */
    private fun executeUpdate(rendered: RenderedSql): Int {
        return connection.prepareStatement(rendered.sql).use { statement ->
            bindParameters(statement, rendered.parameters)
            statement.executeUpdate()
        }
    }

    /**
     * Executa INSERT e retorna as chaves geradas
     */
    fun <T : Any> executeInsertReturningKeys(query: InsertQuery<T>): List<Long> {
        val renderer = InsertRenderer(dialect)
        val rendered = renderer.render(query)

        return connection.prepareStatement(
            rendered.sql,
            PreparedStatement.RETURN_GENERATED_KEYS
        ).use { statement ->
            bindParameters(statement, rendered.parameters)
            statement.executeUpdate()

            val keys = mutableListOf<Long>()
            statement.generatedKeys.use { resultSet ->
                while (resultSet.next()) {
                    keys.add(resultSet.getLong(1))
                }
            }
            keys
        }
    }

    /**
     * Bind de parâmetros no PreparedStatement
     */
    private fun bindParameters(statement: PreparedStatement, parameters: List<Any?>) {
        parameters.forEachIndexed { index, value ->
            val paramIndex = index + 1
            when (value) {
                null -> statement.setObject(paramIndex, null)
                is String -> statement.setString(paramIndex, value)
                is Int -> statement.setInt(paramIndex, value)
                is Long -> statement.setLong(paramIndex, value)
                is Double -> statement.setDouble(paramIndex, value)
                is Float -> statement.setFloat(paramIndex, value)
                is Boolean -> statement.setBoolean(paramIndex, value)
                is java.sql.Date -> statement.setDate(paramIndex, value)
                is java.sql.Timestamp -> statement.setTimestamp(paramIndex, value)
                is java.math.BigDecimal -> statement.setBigDecimal(paramIndex, value)
                else -> statement.setObject(paramIndex, value)
            }
        }
    }

    /**
     * Mapeia um ResultSet para uma lista de mapas
     */
    private fun mapResultSet(resultSet: ResultSet): List<Map<String, Any?>> {
        val results = mutableListOf<Map<String, Any?>>()
        val metaData = resultSet.metaData
        val columnCount = metaData.columnCount

        while (resultSet.next()) {
            val row = mutableMapOf<String, Any?>()

            for (i in 1..columnCount) {
                val columnName = metaData.getColumnLabel(i) ?: metaData.getColumnName(i)
                val value = resultSet.getObject(i)
                row[columnName] = value
            }

            results.add(row)
        }

        return results
    }
}
