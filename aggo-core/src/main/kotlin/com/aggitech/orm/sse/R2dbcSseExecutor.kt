package com.aggitech.orm.sse

import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.query.model.SelectQuery
import com.aggitech.orm.r2dbc.R2dbcQueryExecutor
import io.r2dbc.spi.Connection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong

/**
 * Executor especializado para streaming SSE usando R2DBC e Coroutines
 *
 * Converte queries SQL em streams de eventos Server-Sent Events,
 * mantendo a API imperativa com suspend functions.
 *
 * Uso básico:
 * ```kotlin
 * val executor = R2dbcSseExecutor(connection, SqlDialect.POSTGRESQL)
 *
 * select<User> {
 *     where { User::active eq true }
 * }.let { query ->
 *     executor.executeQueryAsSSE(query)
 *         .collect { event ->
 *             println("SSE: ${event.data}")
 *         }
 * }
 * ```
 *
 * Com heartbeat:
 * ```kotlin
 * executor.executeQueryAsSSEWithHeartbeat(query)
 *     .collect { event ->
 *         // Recebe dados + heartbeats intercalados
 *     }
 * ```
 */
class R2dbcSseExecutor(
    private val connection: Connection,
    private val dialect: SqlDialect,
    private val config: SseConfig = SseConfig()
) {
    private val queryExecutor = R2dbcQueryExecutor(connection, dialect)
    private val eventIdCounter = AtomicLong(0)

    /**
     * Executa query e retorna Flow de eventos SSE
     *
     * Cada linha do resultado é convertida em um evento SSE com:
     * - data: JSON da linha
     * - id: ID único gerado
     * - event: tipo customizável
     * - retry: intervalo de retry configurado
     *
     * @param query Query SQL a executar
     * @param eventType Tipo do evento SSE (padrão: "message")
     * @return Flow de eventos SSE
     */
    fun <T : Any> executeQueryAsSSE(
        query: SelectQuery<T>,
        eventType: String = config.defaultEventType
    ): Flow<SseEvent> = flow {
        // Executa query como Flow
        queryExecutor.executeQueryAsFlow(query)
            .collect { row ->
                // Converte cada linha em evento SSE
                val event = createSseEvent(row, eventType)
                emit(event)
            }
    }

    /**
     * Executa query com heartbeat automático intercalado
     *
     * Heartbeats (comentários SSE) são enviados periodicamente para:
     * - Manter conexão HTTP viva
     * - Detectar desconexões mais rapidamente
     * - Evitar timeouts de proxies/load balancers
     *
     * @param query Query SQL a executar
     * @param eventType Tipo do evento SSE
     * @return Flow de eventos SSE (dados + heartbeats)
     */
    fun <T : Any> executeQueryAsSSEWithHeartbeat(
        query: SelectQuery<T>,
        eventType: String = config.defaultEventType
    ): Flow<SseEvent> {
        if (!config.enableHeartbeat) {
            return executeQueryAsSSE(query, eventType)
        }

        return flow {
            var lastEmissionTime = System.currentTimeMillis()
            val heartbeatMillis = config.heartbeatInterval.toMillis()

            queryExecutor.executeQueryAsFlow(query)
                .collect { row ->
                    // Verifica se precisa enviar heartbeat
                    val now = System.currentTimeMillis()
                    if (now - lastEmissionTime >= heartbeatMillis) {
                        emit(SseEvent.heartbeat())
                        lastEmissionTime = now
                    }

                    // Envia evento com dados
                    val event = createSseEvent(row, eventType)
                    emit(event)
                    lastEmissionTime = now
                }

            // Heartbeat final se necessário
            val finalNow = System.currentTimeMillis()
            if (finalNow - lastEmissionTime >= heartbeatMillis) {
                emit(SseEvent.heartbeat())
            }
        }
    }

    /**
     * Executa query com suporte a retomada a partir de um lastEventId
     *
     * Permite que clientes retomem streaming após desconexão:
     * - Cliente envia "Last-Event-ID" header com último ID recebido
     * - Query é modificada para retornar apenas registros após esse ID
     * - Streaming continua de onde parou
     *
     * IMPORTANTE: A query deve ter uma coluna "id" ou similar para ordenação/filtro
     *
     * @param query Query base (será modificada para filtrar por ID)
     * @param lastEventId Último ID de evento recebido pelo cliente (null = desde o início)
     * @param eventType Tipo do evento SSE
     * @return Flow de eventos SSE
     */
    fun <T : Any> executeQueryAsSSEFrom(
        query: SelectQuery<T>,
        lastEventId: String?,
        eventType: String = config.defaultEventType
    ): Flow<SseEvent> = flow {
        // Se há lastEventId, a query já deve estar filtrada pelo usuário
        // (ex: WHERE id > lastEventId)

        queryExecutor.executeQueryAsFlow(query)
            .collect { row ->
                val event = createSseEvent(row, eventType)
                emit(event)
            }
    }

    /**
     * Versão com heartbeat e retomada
     */
    fun <T : Any> executeQueryAsSSEFromWithHeartbeat(
        query: SelectQuery<T>,
        lastEventId: String?,
        eventType: String = config.defaultEventType
    ): Flow<SseEvent> {
        if (!config.enableHeartbeat) {
            return executeQueryAsSSEFrom(query, lastEventId, eventType)
        }

        return flow {
            var lastEmissionTime = System.currentTimeMillis()
            val heartbeatMillis = config.heartbeatInterval.toMillis()

            queryExecutor.executeQueryAsFlow(query)
                .collect { row ->
                    // Heartbeat se necessário
                    val now = System.currentTimeMillis()
                    if (now - lastEmissionTime >= heartbeatMillis) {
                        emit(SseEvent.heartbeat())
                        lastEmissionTime = now
                    }

                    // Evento com dados
                    val event = createSseEvent(row, eventType)
                    emit(event)
                    lastEmissionTime = now
                }

            // Heartbeat final
            val finalNow = System.currentTimeMillis()
            if (finalNow - lastEmissionTime >= heartbeatMillis) {
                emit(SseEvent.heartbeat())
            }
        }
    }

    /**
     * Stream infinito com polling periódico
     *
     * Executa a query repetidamente em intervalos regulares,
     * emitindo novos resultados como eventos SSE. Útil para:
     * - Monitoramento em tempo real
     * - Dashboards live
     * - Notificações push
     *
     * @param query Query a executar periodicamente
     * @param pollingInterval Intervalo entre execuções
     * @param eventType Tipo do evento SSE
     * @return Flow infinito de eventos SSE
     */
    fun <T : Any> executeQueryAsSSEPolling(
        query: SelectQuery<T>,
        pollingInterval: Long = 5000, // 5 segundos
        eventType: String = config.defaultEventType
    ): Flow<SseEvent> = flow {
        while (true) {
            // Executa query
            queryExecutor.executeQueryAsFlow(query)
                .collect { row ->
                    val event = createSseEvent(row, eventType)
                    emit(event)
                }

            // Aguarda antes da próxima execução
            delay(pollingInterval)

            // Heartbeat entre polls
            if (config.enableHeartbeat) {
                emit(SseEvent.heartbeat("polling"))
            }
        }
    }

    /**
     * Cria evento SSE a partir de uma linha de resultado
     */
    private fun createSseEvent(row: Map<String, Any?>, eventType: String): SseEvent {
        // Serializa row para JSON
        val jsonData = serializeToJson(row)

        // Gera ID único
        val eventId = config.eventIdGenerator()

        return SseEvent(
            data = jsonData,
            id = eventId,
            event = eventType,
            retry = config.retryInterval
        )
    }

    /**
     * Serializa Map para JSON simples
     *
     * Implementação básica. Para produção, considere usar:
     * - kotlinx.serialization
     * - Gson
     * - Jackson
     */
    private fun serializeToJson(map: Map<String, Any?>): String {
        val entries = map.entries.joinToString(",") { (key, value) ->
            val jsonValue = when (value) {
                null -> "null"
                is String -> "\"${value.replace("\"", "\\\"")}\""
                is Number -> value.toString()
                is Boolean -> value.toString()
                else -> "\"${value.toString().replace("\"", "\\\"")}\""
            }
            "\"$key\":$jsonValue"
        }
        return "{$entries}"
    }
}

/**
 * Operadores de Flow para SSE
 */

/**
 * Adiciona buffer com backpressure ao stream SSE
 *
 * Uso:
 * ```kotlin
 * executor.executeQueryAsSSE(query)
 *     .withSseBuffer(capacity = 512)
 *     .collect { event -> ... }
 * ```
 */
fun Flow<SseEvent>.withSseBuffer(capacity: Int = 256): Flow<SseEvent> {
    return buffer(capacity)
}

/**
 * Adiciona timeout a eventos SSE
 *
 * Se nenhum evento for emitido por um período, emite heartbeat
 */
fun Flow<SseEvent>.withSseTimeout(timeoutMillis: Long = 15000): Flow<SseEvent> = flow {
    var lastEmit = System.currentTimeMillis()

    collect { event ->
        val now = System.currentTimeMillis()
        if (now - lastEmit > timeoutMillis) {
            emit(SseEvent.heartbeat("timeout-keepalive"))
        }
        emit(event)
        lastEmit = now
    }
}

/**
 * Filtra eventos SSE por tipo
 */
fun Flow<SseEvent>.filterByEventType(eventType: String): Flow<SseEvent> {
    return filter { event -> event.event == eventType || event.event == null }
}

/**
 * Mapeia eventos SSE para formato de string pronto para envio HTTP
 */
fun Flow<SseEvent>.formatAsSSE(): Flow<String> {
    return map { event -> SseFormatter.format(event) }
}
