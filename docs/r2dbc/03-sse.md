# Server-Sent Events (SSE) - Streaming em Tempo Real

O AggORM fornece suporte a Server-Sent Events (SSE) para streaming de dados em tempo real usando R2DBC.

## Visão Geral

SSE (Server-Sent Events) é um padrão que permite ao servidor enviar atualizações em tempo real para o cliente através de uma conexão HTTP persistente. O AggORM integra SSE com queries R2DBC para streaming eficiente de dados.

## SseEvent

Classe que representa um evento SSE conforme RFC 6455:

```kotlin
import com.aggitech.orm.sse.SseEvent

val event = SseEvent(
    data = """{"id": 1, "name": "John"}""",
    id = "event-1",
    event = "user-update",
    retry = 5000,
    comment = "User data update"
)
```

### Propriedades

| Propriedade | Tipo | Descrição |
|-------------|------|-----------|
| `data` | String | Dados do evento (obrigatório) |
| `id` | String? | ID do evento para reconexão |
| `event` | String? | Nome/tipo do evento |
| `retry` | Int? | Tempo de retry em ms |
| `comment` | String? | Comentário (ignorado pelo cliente) |

### Formatação SSE

```kotlin
val event = SseEvent(
    data = "Hello World",
    id = "1",
    event = "message"
)

println(event.format())
// Output:
// id: 1
// event: message
// data: Hello World
//
```

## Configuração SSE

### SseConfig

Configuração para streaming SSE:

```kotlin
import com.aggitech.orm.sse.SseConfig

val config = SseConfig(
    heartbeatInterval = 30_000L,     // Intervalo de heartbeat (30s)
    retryInterval = 5_000,            // Retry para reconexão (5s)
    bufferSize = 100,                 // Tamanho do buffer
    eventIdGenerator = { UUID.randomUUID().toString() }  // Gerador de IDs
)
```

### Propriedades de Configuração

| Propriedade | Tipo | Default | Descrição |
|-------------|------|---------|-----------|
| `heartbeatInterval` | Long | `30000` | Intervalo entre heartbeats (ms) |
| `retryInterval` | Int | `5000` | Tempo de retry para cliente (ms) |
| `bufferSize` | Int | `100` | Tamanho do buffer de eventos |
| `eventIdGenerator` | () -> String | UUID | Função geradora de IDs |

## Streaming de Queries

### toSSE() - Streaming Básico

Converte uma query em stream SSE:

```kotlin
import com.aggitech.orm.sse.toSSE
import kotlinx.coroutines.flow.collect

suspend fun streamUsers() {
    select<User> {
        where { User::active eq true }
        orderBy(User::createdAt, OrderDirection.DESC)
    }
    .toSSE()
    .collect { event ->
        // Cada registro é um evento SSE
        println(event.format())
    }
}
```

### toSSEWithHeartbeat() - Com Heartbeat

Adiciona heartbeats para manter a conexão ativa:

```kotlin
import com.aggitech.orm.sse.toSSEWithHeartbeat

suspend fun streamWithHeartbeat() {
    select<User> {
        where { User::active eq true }
    }
    .toSSEWithHeartbeat(
        heartbeatInterval = 30_000L  // 30 segundos
    )
    .collect { event ->
        when {
            event.comment == ":heartbeat" -> {
                // Evento de heartbeat - mantém conexão
                println("Heartbeat received")
            }
            else -> {
                // Evento de dados
                println("Data: ${event.data}")
            }
        }
    }
}
```

### toSSEFrom() - Reconexão

Retoma streaming a partir de um ID específico (para reconexão):

```kotlin
import com.aggitech.orm.sse.toSSEFrom

suspend fun resumeStream(lastEventId: String) {
    select<User> {
        where { User::active eq true }
    }
    .toSSEFrom(lastEventId)
    .collect { event ->
        // Recebe apenas eventos após o lastEventId
        println("Event ID: ${event.id}, Data: ${event.data}")
    }
}
```

### toSSEPolling() - Modo Polling

Streaming com polling periódico para novas atualizações:

```kotlin
import com.aggitech.orm.sse.toSSEPolling

suspend fun pollForUpdates() {
    select<User> {
        where { User::updatedAt gte lastCheck }
    }
    .toSSEPolling(
        interval = 5_000L,        // Poll a cada 5 segundos
        maxEvents = 100           // Máximo de eventos por poll
    )
    .collect { event ->
        println("New update: ${event.data}")
    }
}
```

## Integração com Web Frameworks

### Spring WebFlux

```kotlin
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import kotlinx.coroutines.flow.Flow

@RestController
@RequestMapping("/api/users")
class UserController {

    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamUsers(): Flow<SseEvent> {
        return select<User> {
            where { User::active eq true }
        }.toSSEWithHeartbeat()
    }

    @GetMapping("/stream/resume", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun resumeStream(
        @RequestHeader("Last-Event-ID") lastEventId: String?
    ): Flow<SseEvent> {
        return if (lastEventId != null) {
            select<User> { }.toSSEFrom(lastEventId)
        } else {
            select<User> { }.toSSE()
        }
    }
}
```

### Ktor

```kotlin
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Application.configureRouting() {
    routing {
        get("/api/users/stream") {
            call.respondTextWriter(ContentType.Text.EventStream) {
                select<User> {
                    where { User::active eq true }
                }
                .toSSEWithHeartbeat()
                .collect { event ->
                    write(event.format())
                    flush()
                }
            }
        }
    }
}
```

## Eventos Customizados

### Criando Eventos Customizados

```kotlin
// Evento de criação de usuário
fun userCreatedEvent(user: User): SseEvent {
    return SseEvent(
        data = Json.encodeToString(user),
        event = "user-created",
        id = "user-${user.id}-created"
    )
}

// Evento de atualização
fun userUpdatedEvent(user: User): SseEvent {
    return SseEvent(
        data = Json.encodeToString(user),
        event = "user-updated",
        id = "user-${user.id}-updated-${System.currentTimeMillis()}"
    )
}

// Evento de exclusão
fun userDeletedEvent(userId: Long): SseEvent {
    return SseEvent(
        data = """{"userId": $userId}""",
        event = "user-deleted",
        id = "user-$userId-deleted"
    )
}
```

### Stream com Tipos de Eventos

```kotlin
suspend fun streamUserChanges(): Flow<SseEvent> = flow {
    // Emitir usuários existentes
    select<User> { }
        .toSSE()
        .collect { emit(it) }

    // Continuar com polling para mudanças
    while (true) {
        delay(5000)

        // Verificar novos usuários
        select<User> {
            where { User::createdAt gte lastCheck }
        }
        .executeQueryAsFlow()
        .collect { user ->
            emit(userCreatedEvent(user.toEntity()))
        }

        // Verificar atualizações
        select<User> {
            where { User::updatedAt gte lastCheck }
        }
        .executeQueryAsFlow()
        .collect { user ->
            emit(userUpdatedEvent(user.toEntity()))
        }
    }
}
```

## Heartbeat

### Propósito do Heartbeat

Heartbeats mantêm a conexão ativa e detectam desconexões:

```kotlin
val heartbeatEvent = SseEvent.heartbeat()
// Output:
// : heartbeat
//
```

### Configurando Intervalo

```kotlin
suspend fun streamWithCustomHeartbeat() {
    val config = SseConfig(
        heartbeatInterval = 15_000L  // 15 segundos
    )

    select<User> { }
        .toSSEWithHeartbeat(config)
        .collect { event ->
            // Processa eventos e heartbeats
        }
}
```

## Reconexão do Cliente

### Rastreando Last-Event-ID

```kotlin
suspend fun handleReconnection(lastEventId: String?) {
    if (lastEventId != null) {
        // Cliente reconectando - enviar apenas eventos novos
        select<User> {
            where { User::eventId gt lastEventId.toLong() }
        }
        .toSSE()
        .collect { emit(it) }
    } else {
        // Nova conexão - enviar todos os dados
        select<User> { }
            .toSSE()
            .collect { emit(it) }
    }
}
```

### Retry Configuration

```kotlin
val event = SseEvent(
    data = "Important data",
    retry = 3000  // Cliente deve tentar reconectar após 3s
)

// Formato enviado:
// retry: 3000
// data: Important data
//
```

## Buffer e Backpressure

### Configurando Buffer

```kotlin
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.channels.BufferOverflow

suspend fun bufferedStream() {
    select<User> { }
        .toSSE()
        .buffer(
            capacity = 50,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        .collect { event ->
            // Processamento lento não bloqueia producer
            processEvent(event)
        }
}
```

### Estratégias de Overflow

| Estratégia | Descrição |
|------------|-----------|
| `SUSPEND` | Suspende producer quando buffer cheio |
| `DROP_OLDEST` | Descarta eventos mais antigos |
| `DROP_LATEST` | Descarta eventos mais recentes |

## Error Handling

### Tratamento de Erros no Stream

```kotlin
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry

suspend fun resilientStream() {
    select<User> { }
        .toSSEWithHeartbeat()
        .retry(3) { e ->
            // Retry até 3 vezes para erros recuperáveis
            e is IOException
        }
        .catch { e ->
            // Emitir evento de erro
            emit(SseEvent(
                data = """{"error": "${e.message}"}""",
                event = "error"
            ))
        }
        .collect { event ->
            println(event.format())
        }
}
```

## Exemplo Completo

### Sistema de Notificações em Tempo Real

```kotlin
import com.aggitech.orm.sse.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

data class Notification(
    val id: Long,
    val userId: Long,
    val message: String,
    val type: String,
    val read: Boolean,
    val createdAt: Timestamp
)

class NotificationService {

    private val config = SseConfig(
        heartbeatInterval = 30_000L,
        retryInterval = 5_000
    )

    /**
     * Stream de notificações para um usuário específico
     */
    fun streamNotifications(userId: Long): Flow<SseEvent> {
        return select<Notification> {
            where {
                (Notification::userId eq userId) and
                (Notification::read eq false)
            }
            orderBy(Notification::createdAt, OrderDirection.DESC)
        }
        .toSSEWithHeartbeat(config)
        .map { event ->
            // Adicionar tipo de evento
            event.copy(event = "notification")
        }
    }

    /**
     * Stream com reconexão
     */
    fun streamFromLastEvent(
        userId: Long,
        lastEventId: String?
    ): Flow<SseEvent> {
        return if (lastEventId != null) {
            val lastId = lastEventId.toLongOrNull() ?: 0

            select<Notification> {
                where {
                    (Notification::userId eq userId) and
                    (Notification::id gt lastId)
                }
            }.toSSE()
        } else {
            streamNotifications(userId)
        }
    }

    /**
     * Polling para novas notificações
     */
    fun pollNotifications(
        userId: Long,
        interval: Long = 5000L
    ): Flow<SseEvent> {
        return select<Notification> {
            where {
                (Notification::userId eq userId) and
                (Notification::read eq false)
            }
        }
        .toSSEPolling(interval = interval)
    }
}

// Controller Spring WebFlux
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val service: NotificationService
) {

    @GetMapping("/stream/{userId}", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(
        @PathVariable userId: Long,
        @RequestHeader("Last-Event-ID") lastEventId: String?
    ): Flow<SseEvent> {
        return service.streamFromLastEvent(userId, lastEventId)
    }
}
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Usar heartbeats para conexões longas
select<User> { }
    .toSSEWithHeartbeat(heartbeatInterval = 30_000L)

// Implementar reconexão com Last-Event-ID
fun stream(lastEventId: String?) = when (lastEventId) {
    null -> fullStream()
    else -> resumeStream(lastEventId)
}

// Usar buffer para clientes lentos
select<User> { }
    .toSSE()
    .buffer(50)
```

### [AVOID] Evitar

```kotlin
// Não enviar dados sensíveis sem autenticação
select<User> { }
    .toSSE()  // [AVOID] Sem verificação de auth

// Não ignorar erros
select<User> { }
    .toSSE()
    .collect { }  // [AVOID] Sem catch

// Não usar intervalos muito curtos
toSSEPolling(interval = 100L)  // [AVOID] Muito frequente
```
