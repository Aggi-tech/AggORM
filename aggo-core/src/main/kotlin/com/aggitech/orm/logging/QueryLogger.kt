package com.aggitech.orm.logging

import com.aggitech.orm.sql.context.RenderedSql
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Interface para logging de queries SQL
 *
 * Permite plugar diferentes implementações de logging
 * (console, SLF4J, custom, etc.)
 */
interface QueryLogger {
    /**
     * Loga uma query antes da execução
     */
    fun logQuery(context: QueryContext)

    /**
     * Loga uma query após a execução
     */
    fun logQueryComplete(context: QueryContext, result: QueryResult)

    /**
     * Loga um erro de query
     */
    fun logQueryError(context: QueryContext, error: Throwable)
}

/**
 * Contexto de uma query sendo executada
 */
data class QueryContext(
    val sql: String,
    val parameters: List<Any?>,
    val queryType: QueryType,
    val configName: String = "default",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Resultado da execução de uma query
 */
data class QueryResult(
    val rowsAffected: Long = 0,
    val executionTime: Duration,
    val success: Boolean = true
)

/**
 * Tipos de queries
 */
enum class QueryType {
    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    TRANSACTION
}

/**
 * Logger de console simples (padrão)
 */
class ConsoleQueryLogger(
    private val logLevel: LogLevel = LogLevel.DEBUG,
    private val includeParameters: Boolean = true,
    private val includeExecutionTime: Boolean = true
) : QueryLogger {

    override fun logQuery(context: QueryContext) {
        if (logLevel >= LogLevel.DEBUG) {
            val msg = buildString {
                append("[AggORM ${context.queryType}] ")
                append(formatSql(context.sql))

                if (includeParameters && context.parameters.isNotEmpty()) {
                    append("\n  Parameters: ${formatParameters(context.parameters)}")
                }
            }
            println(msg)
        }
    }

    override fun logQueryComplete(context: QueryContext, result: QueryResult) {
        if (logLevel >= LogLevel.DEBUG) {
            val msg = buildString {
                append("[AggORM ${context.queryType}] Completed")

                if (includeExecutionTime) {
                    append(" in ${formatDuration(result.executionTime)}")
                }

                if (result.rowsAffected > 0) {
                    append(" - ${result.rowsAffected} row(s) affected")
                }
            }
            println(msg)
        }
    }

    override fun logQueryError(context: QueryContext, error: Throwable) {
        if (logLevel >= LogLevel.ERROR) {
            val msg = buildString {
                append("[AggORM ${context.queryType}] ERROR: ${error.message}")
                append("\n  SQL: ${formatSql(context.sql)}")

                if (includeParameters && context.parameters.isNotEmpty()) {
                    append("\n  Parameters: ${formatParameters(context.parameters)}")
                }
            }
            System.err.println(msg)
        }
    }

    private fun formatSql(sql: String): String {
        return sql.trim().replace(Regex("\\s+"), " ")
    }

    private fun formatParameters(params: List<Any?>): String {
        return params.joinToString(", ") { param ->
            when (param) {
                null -> "null"
                is String -> "'$param'"
                else -> param.toString()
            }
        }
    }

    private fun formatDuration(duration: Duration): String {
        return when {
            duration.inWholeMilliseconds < 1 -> "${duration.inWholeMicroseconds}µs"
            duration.inWholeSeconds < 1 -> "${duration.inWholeMilliseconds}ms"
            else -> "${duration.inWholeSeconds}s"
        }
    }
}

/**
 * Logger que não faz nada (para desabilitar logging)
 */
object NoOpQueryLogger : QueryLogger {
    override fun logQuery(context: QueryContext) {}
    override fun logQueryComplete(context: QueryContext, result: QueryResult) {}
    override fun logQueryError(context: QueryContext, error: Throwable) {}
}

/**
 * Níveis de log
 */
enum class LogLevel(val priority: Int) {
    TRACE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    OFF(5)
}

/**
 * Gerenciador global de logging
 */
object QueryLoggerManager {
    private var globalLogger: QueryLogger = NoOpQueryLogger
    private val loggers = mutableMapOf<String, QueryLogger>()

    /**
     * Define o logger global (padrão para todos os configs)
     */
    fun setGlobalLogger(logger: QueryLogger) {
        globalLogger = logger
    }

    /**
     * Define um logger específico para um config
     */
    fun setLogger(configName: String, logger: QueryLogger) {
        loggers[configName] = logger
    }

    /**
     * Obtém o logger para um config (usa global se não houver específico)
     */
    fun getLogger(configName: String = "default"): QueryLogger {
        return loggers[configName] ?: globalLogger
    }

    /**
     * Habilita logging de console com configurações padrão
     */
    fun enableConsoleLogging(
        logLevel: LogLevel = LogLevel.DEBUG,
        includeParameters: Boolean = true,
        includeExecutionTime: Boolean = true
    ) {
        setGlobalLogger(ConsoleQueryLogger(logLevel, includeParameters, includeExecutionTime))
    }

    /**
     * Desabilita logging
     */
    fun disableLogging() {
        setGlobalLogger(NoOpQueryLogger)
    }
}

/**
 * Helper para medir tempo de execução e logar
 */
inline fun <T> QueryLogger.loggedExecution(
    context: QueryContext,
    block: () -> T
): T {
    logQuery(context)

    val startTime = System.nanoTime()

    return try {
        val result = block()
        val executionTime = (System.nanoTime() - startTime).toDuration(DurationUnit.NANOSECONDS)

        val rowsAffected = when (result) {
            is Int -> result.toLong()
            is Long -> result
            is List<*> -> result.size.toLong()
            else -> 0L
        }

        logQueryComplete(
            context,
            QueryResult(
                rowsAffected = rowsAffected,
                executionTime = executionTime,
                success = true
            )
        )

        result
    } catch (e: Exception) {
        logQueryError(context, e)
        throw e
    }
}
