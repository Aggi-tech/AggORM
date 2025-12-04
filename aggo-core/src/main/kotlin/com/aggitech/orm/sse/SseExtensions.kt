package com.aggitech.orm.sse

import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.query.model.SelectQuery
import io.r2dbc.spi.Connection
import kotlinx.coroutines.flow.Flow

/**
 * Extensões para queries que adicionam suporte a Server-Sent Events (SSE)
 *
 * Mantém a mesma DSL e ergonomia das extensões R2DBC existentes,
 * adicionando funcionalidade de streaming SSE com API imperativa.
 *
 * Exemplos:
 * ```kotlin
 * // Streaming básico
 * select<User> {
 *     where { User::active eq true }
 * }.toSSE(connection, SqlDialect.POSTGRESQL)
 *   .collect { event ->
 *       println("Event: ${event.id} - ${event.data}")
 *   }
 *
 * // Com heartbeat
 * select<Order> { ... }
 *     .toSSEWithHeartbeat(connection, dialect)
 *     .collect { event -> processEvent(event) }
 *
 * // Com retomada
 * select<Notification> {
 *     where { Notification::id gt lastId }
 * }.toSSEFrom(connection, dialect, lastEventId)
 *   .collect { event -> ... }
 * ```
 */

/**
 * Converte query em stream SSE
 *
 * Executa a query e retorna um Flow de eventos SSE.
 * Cada linha do resultado é convertida em um evento com:
 * - data: JSON da linha
 * - id: ID único gerado
 * - event: tipo customizável
 * - retry: intervalo configurado
 *
 * Uso:
 * ```kotlin
 * val events = select<User> {
 *     where { User::age gte 18 }
 *     orderBy { User::createdAt.desc() }
 * }.toSSE(connection, SqlDialect.POSTGRESQL)
 *
 * events.collect { event ->
 *     println("SSE Event ${event.id}: ${event.data}")
 * }
 * ```
 *
 * @param connection Conexão R2DBC
 * @param dialect Dialeto SQL
 * @param config Configuração SSE (opcional)
 * @return Flow de eventos SSE
 */
fun <T : Any> SelectQuery<T>.toSSE(
    connection: Connection,
    dialect: SqlDialect,
    config: SseConfig = SseConfig()
): Flow<SseEvent> {
    val executor = R2dbcSseExecutor(connection, dialect, config)
    return executor.executeQueryAsSSE(this)
}

/**
 * Converte query em stream SSE com heartbeat automático
 *
 * Adiciona mensagens de heartbeat (comentários SSE) periodicamente para:
 * - Manter conexão HTTP viva
 * - Detectar desconexões rapidamente
 * - Evitar timeouts de proxies/load balancers
 *
 * Uso:
 * ```kotlin
 * select<Order> {
 *     where { Order::status eq "PENDING" }
 * }.toSSEWithHeartbeat(connection, SqlDialect.POSTGRESQL)
 *   .collect { event ->
 *       if (event.comment != null) {
 *           println("Heartbeat received")
 *       } else {
 *           processOrder(event.data)
 *       }
 *   }
 * ```
 *
 * @param connection Conexão R2DBC
 * @param dialect Dialeto SQL
 * @param config Configuração SSE (heartbeatInterval é usado)
 * @return Flow de eventos SSE incluindo heartbeats
 */
fun <T : Any> SelectQuery<T>.toSSEWithHeartbeat(
    connection: Connection,
    dialect: SqlDialect,
    config: SseConfig = SseConfig()
): Flow<SseEvent> {
    val executor = R2dbcSseExecutor(connection, dialect, config)
    return executor.executeQueryAsSSEWithHeartbeat(this)
}

/**
 * Converte query em stream SSE com suporte a retomada
 *
 * Permite que clientes retomem o streaming após desconexão:
 * - Cliente envia "Last-Event-ID" header com último ID recebido
 * - Query deve ser filtrada para retornar apenas registros após esse ID
 * - Streaming continua de onde parou
 *
 * IMPORTANTE: A query deve incluir filtro por ID quando lastEventId != null
 *
 * Uso:
 * ```kotlin
 * val lastId = request.headers["Last-Event-ID"]
 *
 * select<Notification> {
 *     if (lastId != null) {
 *         where { Notification::id gt lastId.toLong() }
 *     }
 *     orderBy { Notification::id.asc() }
 * }.toSSEFrom(connection, SqlDialect.POSTGRESQL, lastId)
 *   .collect { event ->
 *       // Salvar event.id para possível retomada
 *       saveLastEventId(event.id)
 *       processNotification(event.data)
 *   }
 * ```
 *
 * @param connection Conexão R2DBC
 * @param dialect Dialeto SQL
 * @param lastEventId Último ID de evento recebido (null = desde o início)
 * @param config Configuração SSE
 * @return Flow de eventos SSE
 */
fun <T : Any> SelectQuery<T>.toSSEFrom(
    connection: Connection,
    dialect: SqlDialect,
    lastEventId: String?,
    config: SseConfig = SseConfig()
): Flow<SseEvent> {
    val executor = R2dbcSseExecutor(connection, dialect, config)
    return executor.executeQueryAsSSEFrom(this, lastEventId)
}

/**
 * Versão com heartbeat e retomada combinados
 *
 * Uso:
 * ```kotlin
 * select<Event> {
 *     if (lastId != null) {
 *         where { Event::id gt lastId.toLong() }
 *     }
 * }.toSSEFromWithHeartbeat(connection, dialect, lastId)
 *   .collect { event -> ... }
 * ```
 */
fun <T : Any> SelectQuery<T>.toSSEFromWithHeartbeat(
    connection: Connection,
    dialect: SqlDialect,
    lastEventId: String?,
    config: SseConfig = SseConfig()
): Flow<SseEvent> {
    val executor = R2dbcSseExecutor(connection, dialect, config)
    return executor.executeQueryAsSSEFromWithHeartbeat(this, lastEventId)
}

/**
 * Converte query em stream SSE com polling periódico
 *
 * Executa a query repetidamente em intervalos regulares,
 * criando um stream infinito de eventos SSE. Útil para:
 * - Monitoramento em tempo real
 * - Dashboards ao vivo
 * - Notificações push
 *
 * Uso:
 * ```kotlin
 * // Monitora pedidos pendentes a cada 5 segundos
 * select<Order> {
 *     where { Order::status eq "PENDING" }
 *     orderBy { Order::createdAt.desc() }
 *     limit(10)
 * }.toSSEPolling(
 *     connection = connection,
 *     dialect = SqlDialect.POSTGRESQL,
 *     pollingInterval = 5000 // 5 segundos
 * ).collect { event ->
 *     updateDashboard(event.data)
 * }
 * ```
 *
 * @param connection Conexão R2DBC
 * @param dialect Dialeto SQL
 * @param pollingInterval Intervalo entre execuções em ms (padrão: 5000ms)
 * @param config Configuração SSE
 * @return Flow infinito de eventos SSE
 */
fun <T : Any> SelectQuery<T>.toSSEPolling(
    connection: Connection,
    dialect: SqlDialect,
    pollingInterval: Long = 5000,
    config: SseConfig = SseConfig()
): Flow<SseEvent> {
    val executor = R2dbcSseExecutor(connection, dialect, config)
    return executor.executeQueryAsSSEPolling(this, pollingInterval)
}

/**
 * Converte query em stream SSE formatado como string
 *
 * Retorna Flow de strings já formatadas no protocolo SSE,
 * prontas para serem enviadas diretamente na resposta HTTP.
 *
 * Uso:
 * ```kotlin
 * select<User> { ... }
 *     .toSSEFormatted(connection, dialect)
 *     .collect { sseString ->
 *         // sseString já está no formato:
 *         // id: 123
 *         // data: {...}
 *         //
 *         response.write(sseString)
 *     }
 * ```
 *
 * @param connection Conexão R2DBC
 * @param dialect Dialeto SQL
 * @param config Configuração SSE
 * @return Flow de strings formatadas em SSE
 */
fun <T : Any> SelectQuery<T>.toSSEFormatted(
    connection: Connection,
    dialect: SqlDialect,
    config: SseConfig = SseConfig()
): Flow<String> {
    return toSSE(connection, dialect, config)
        .formatAsSSE()
}

/**
 * Versão com heartbeat formatada
 */
fun <T : Any> SelectQuery<T>.toSSEFormattedWithHeartbeat(
    connection: Connection,
    dialect: SqlDialect,
    config: SseConfig = SseConfig()
): Flow<String> {
    return toSSEWithHeartbeat(connection, dialect, config)
        .formatAsSSE()
}

/**
 * Helper para criar configuração SSE customizada inline
 *
 * Uso:
 * ```kotlin
 * select<User> { ... }
 *     .toSSE(connection, dialect, sseConfig {
 *         heartbeatInterval = Duration.ofSeconds(30)
 *         retryInterval = 5000
 *         bufferSize = 512
 *     })
 * ```
 */
inline fun <T : Any> SelectQuery<T>.toSSEWith(
    connection: Connection,
    dialect: SqlDialect,
    noinline configBuilder: SseConfig.Builder.() -> Unit
): Flow<SseEvent> {
    val config = sseConfig(configBuilder)
    return toSSE(connection, dialect, config)
}
