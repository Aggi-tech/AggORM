package com.aggitech.orm.jdbc

import com.aggitech.orm.logging.*
import com.aggitech.orm.query.model.*
import com.aggitech.orm.sql.renderer.*

/**
 * Extensões JDBC ergonômicas para queries
 *
 * Usa o JdbcConnectionManager para gerenciar conexões automaticamente.
 * Não é necessário passar Connection ou SqlDialect manualmente.
 */

/**
 * Executa uma query SELECT usando o connection manager
 *
 * Uso:
 * ```kotlin
 * // Registra configuração uma vez no início da aplicação
 * JdbcConnectionManager.register(config = myDbConfig)
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
fun <T : Any> SelectQuery<T>.execute(configName: String = "default"): List<Map<String, Any?>> {
    return JdbcConnectionManager.withConnection(configName) { connection ->
        val dialect = JdbcConnectionManager.getDialect(configName)
        val renderer = SelectRenderer(dialect)
        val rendered = renderer.render(this)

        val logger = QueryLoggerManager.getLogger(configName)
        val context = QueryContext(
            sql = rendered.sql,
            parameters = rendered.parameters,
            queryType = QueryType.SELECT,
            configName = configName
        )

        logger.loggedExecution(context) {
            connection.prepareStatement(rendered.sql).use { statement ->
                bindParameters(statement, rendered.parameters)

                statement.executeQuery().use { resultSet ->
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

                    results
                }
            }
        }
    }
}

/**
 * Executa uma query INSERT usando o connection manager
 *
 * @param configName Nome da configuração registrada
 * @return Número de linhas afetadas
 */
fun <T : Any> InsertQuery<T>.execute(configName: String = "default"): Int {
    return JdbcConnectionManager.withConnection(configName) { connection ->
        val dialect = JdbcConnectionManager.getDialect(configName)
        val renderer = InsertRenderer(dialect)
        val rendered = renderer.render(this)

        connection.prepareStatement(rendered.sql).use { statement ->
            bindParameters(statement, rendered.parameters)
            statement.executeUpdate()
        }
    }
}

/**
 * Executa uma query INSERT e retorna as chaves geradas
 *
 * @param configName Nome da configuração registrada
 * @return Lista de IDs gerados
 */
fun <T : Any> InsertQuery<T>.executeReturningKeys(configName: String = "default"): List<Long> {
    return JdbcConnectionManager.withConnection(configName) { connection ->
        val dialect = JdbcConnectionManager.getDialect(configName)
        val renderer = InsertRenderer(dialect)
        val rendered = renderer.render(this)

        connection.prepareStatement(
            rendered.sql,
            java.sql.Statement.RETURN_GENERATED_KEYS
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
}

/**
 * Executa uma query UPDATE usando o connection manager
 *
 * @param configName Nome da configuração registrada
 * @return Número de linhas afetadas
 */
fun <T : Any> UpdateQuery<T>.execute(configName: String = "default"): Int {
    return JdbcConnectionManager.withConnection(configName) { connection ->
        val dialect = JdbcConnectionManager.getDialect(configName)
        val renderer = UpdateRenderer(dialect)
        val rendered = renderer.render(this)

        connection.prepareStatement(rendered.sql).use { statement ->
            bindParameters(statement, rendered.parameters)
            statement.executeUpdate()
        }
    }
}

/**
 * Executa uma query DELETE usando o connection manager
 *
 * @param configName Nome da configuração registrada
 * @return Número de linhas afetadas
 */
fun <T : Any> DeleteQuery<T>.execute(configName: String = "default"): Int {
    return JdbcConnectionManager.withConnection(configName) { connection ->
        val dialect = JdbcConnectionManager.getDialect(configName)
        val renderer = DeleteRenderer(dialect)
        val rendered = renderer.render(this)

        connection.prepareStatement(rendered.sql).use { statement ->
            bindParameters(statement, rendered.parameters)
            statement.executeUpdate()
        }
    }
}

/**
 * Bind de parâmetros no PreparedStatement
 */
private fun bindParameters(statement: java.sql.PreparedStatement, parameters: List<Any?>) {
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
 * Executa múltiplas queries em uma transação
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
fun <T> transaction(configName: String = "default", block: () -> T): T {
    return JdbcConnectionManager.withConnection(configName, autoCommit = false) { connection ->
        block()
    }
}

// ==================== Extensões com Mapeamento Automático ====================

/**
 * Executa uma query SELECT e mapeia para entidades
 *
 * Uso:
 * ```kotlin
 * val users: List<User> = select<User> {
 *     where { User::age gte 18 }
 * }.executeAsEntities()
 * ```
 */
inline fun <reified T : Any> com.aggitech.orm.query.model.SelectQuery<T>.executeAsEntities(
    configName: String = "default"
): List<T> {
    val results = execute(configName)
    return com.aggitech.orm.mapping.EntityMapper.mapList(results, T::class)
}

/**
 * Executa uma query SELECT e retorna a primeira entidade ou null
 *
 * Uso:
 * ```kotlin
 * val user: User? = select<User> {
 *     where { User::id eq 1L }
 * }.executeAsEntityOrNull()
 * ```
 */
inline fun <reified T : Any> com.aggitech.orm.query.model.SelectQuery<T>.executeAsEntityOrNull(
    configName: String = "default"
): T? {
    val results = execute(configName)
    return results.firstOrNull()?.let { com.aggitech.orm.mapping.EntityMapper.map(it, T::class) }
}

/**
 * Executa uma query SELECT e retorna a primeira entidade ou lança exceção
 *
 * @throws NoSuchElementException se não houver resultado
 */
inline fun <reified T : Any> com.aggitech.orm.query.model.SelectQuery<T>.executeAsEntity(
    configName: String = "default"
): T {
    return executeAsEntityOrNull(configName)
        ?: throw NoSuchElementException("No entity found for query")
}
