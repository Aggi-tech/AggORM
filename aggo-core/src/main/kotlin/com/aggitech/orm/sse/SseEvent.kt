package com.aggitech.orm.sse

/**
 * Representa um evento Server-Sent Event (SSE) seguindo a especificação RFC 6455
 *
 * Uso:
 * ```kotlin
 * val event = SseEvent(
 *     data = """{"user":"John","age":30}""",
 *     id = "123",
 *     event = "user-update",
 *     retry = 3000
 * )
 * ```
 *
 * Ou usando builder:
 * ```kotlin
 * val event = sseEvent("data content") {
 *     id = "123"
 *     event = "custom-event"
 *     retry = 5000
 *     comment = "This is a comment"
 * }
 * ```
 */
data class SseEvent(
    /**
     * O conteúdo do evento. Pode conter múltiplas linhas.
     * Cada linha será prefixada com "data: " no protocolo SSE
     */
    val data: String,

    /**
     * ID único do evento. Usado pelo cliente para retomar streaming após desconexão.
     * O navegador envia este ID no header "Last-Event-ID" em reconexões.
     */
    val id: String? = null,

    /**
     * Tipo do evento. Se especificado, o cliente pode filtrar eventos por tipo.
     * Ex: "message", "user-update", "notification"
     */
    val event: String? = null,

    /**
     * Tempo em milissegundos que o cliente deve aguardar antes de tentar reconectar
     * após uma desconexão. Sobrescreve o padrão do navegador.
     */
    val retry: Long? = null,

    /**
     * Comentário que será enviado como linha começando com ":"
     * Útil para heartbeat ou debug. Navegadores ignoram comentários.
     */
    val comment: String? = null
) {
    /**
     * Builder fluente para construção de eventos SSE
     */
    class Builder(private var data: String) {
        var id: String? = null
        var event: String? = null
        var retry: Long? = null
        var comment: String? = null

        fun build(): SseEvent = SseEvent(
            data = data,
            id = id,
            event = event,
            retry = retry,
            comment = comment
        )
    }

    companion object {
        /**
         * Cria um evento SSE de heartbeat (apenas comentário, sem dados)
         *
         * Uso:
         * ```kotlin
         * val heartbeat = SseEvent.heartbeat()
         * // Resultado: ": heartbeat\n\n"
         * ```
         */
        fun heartbeat(message: String = "heartbeat"): SseEvent {
            return SseEvent(
                data = "",
                comment = message
            )
        }

        /**
         * Cria um evento SSE simples apenas com dados
         */
        fun data(content: String): SseEvent {
            return SseEvent(data = content)
        }

        /**
         * Cria um evento SSE com dados e ID
         */
        fun dataWithId(content: String, id: String): SseEvent {
            return SseEvent(data = content, id = id)
        }
    }
}

/**
 * Função DSL para criar eventos SSE com builder
 *
 * Uso:
 * ```kotlin
 * val event = sseEvent("Hello World") {
 *     id = "msg-123"
 *     event = "greeting"
 *     retry = 3000
 * }
 * ```
 */
fun sseEvent(data: String, builder: SseEvent.Builder.() -> Unit = {}): SseEvent {
    return SseEvent.Builder(data).apply(builder).build()
}
