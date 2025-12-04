package com.aggitech.orm.sse

/**
 * Formatador de eventos SSE seguindo a especificação RFC 6455
 *
 * Formato SSE:
 * ```
 * id: 123
 * event: message
 * retry: 3000
 * data: linha 1
 * data: linha 2
 *
 * ```
 *
 * Regras:
 * - Cada campo começa em uma nova linha
 * - Campos são no formato "campo: valor"
 * - Data pode ter múltiplas linhas (cada uma prefixada com "data: ")
 * - Comentários começam com ":"
 * - Eventos terminam com linha em branco dupla ("\n\n")
 */
object SseFormatter {

    /**
     * Formata um evento SSE completo
     *
     * Uso:
     * ```kotlin
     * val event = SseEvent(data = "Hello", id = "1", event = "message")
     * val formatted = SseFormatter.format(event)
     * // Resultado:
     * // id: 1
     * // event: message
     * // data: Hello
     * //
     * ```
     */
    fun format(event: SseEvent): String {
        val builder = StringBuilder()

        // Comentário (se presente)
        event.comment?.let { comment ->
            builder.append(formatComment(comment))
        }

        // ID (se presente)
        event.id?.let { id ->
            builder.append("id: $id\n")
        }

        // Event type (se presente)
        event.event?.let { eventType ->
            builder.append("event: $eventType\n")
        }

        // Retry (se presente)
        event.retry?.let { retry ->
            builder.append("retry: $retry\n")
        }

        // Data (sempre presente, mas pode ser vazio para heartbeats)
        if (event.data.isNotEmpty()) {
            builder.append(formatData(event.data))
        }

        // Linha em branco final que marca fim do evento
        builder.append("\n")

        return builder.toString()
    }

    /**
     * Formata apenas o campo data, tratando múltiplas linhas
     *
     * Exemplo:
     * ```kotlin
     * formatData("linha1\nlinha2")
     * // Resultado:
     * // data: linha1
     * // data: linha2
     * //
     * ```
     */
    fun formatData(data: String): String {
        return data.lines().joinToString("\n") { line ->
            "data: $line"
        } + "\n"
    }

    /**
     * Formata um comentário SSE
     *
     * Comentários são linhas que começam com ":"
     * Navegadores ignoram comentários, mas são úteis para:
     * - Heartbeat/keep-alive
     * - Debug e logging
     * - Metadados
     *
     * Exemplo:
     * ```kotlin
     * formatComment("heartbeat")
     * // Resultado: ": heartbeat\n"
     * ```
     */
    fun formatComment(comment: String): String {
        return comment.lines().joinToString("\n") { line ->
            ": $line"
        } + "\n"
    }

    /**
     * Formata um evento de heartbeat (apenas comentário)
     *
     * Uso:
     * ```kotlin
     * val heartbeat = SseFormatter.formatHeartbeat()
     * // Resultado: ": heartbeat\n\n"
     * ```
     */
    fun formatHeartbeat(comment: String = "heartbeat"): String {
        return "${formatComment(comment)}\n"
    }

    /**
     * Formata um evento SSE completo com data, id e retry
     *
     * Atalho para evitar criar objeto SseEvent quando só precisa dos campos básicos
     *
     * Uso:
     * ```kotlin
     * val sse = SseFormatter.formatEvent(
     *     data = """{"user":"John"}""",
     *     eventId = "123",
     *     retry = 3000
     * )
     * ```
     */
    fun formatEvent(
        data: String,
        eventId: String? = null,
        eventType: String? = null,
        retry: Long? = null
    ): String {
        val event = SseEvent(
            data = data,
            id = eventId,
            event = eventType,
            retry = retry
        )
        return format(event)
    }

    /**
     * Escapa caracteres especiais em dados JSON para SSE
     *
     * Garante que newlines e caracteres especiais não quebrem o formato SSE
     */
    fun escapeData(data: String): String {
        return data
            .replace("\\", "\\\\")  // Escape backslash
            .replace("\n", "\\n")   // Escape newlines
            .replace("\r", "\\r")   // Escape carriage return
    }

    /**
     * Remove escape de dados SSE
     */
    fun unescapeData(data: String): String {
        return data
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\\", "\\")
    }

    /**
     * Valida se uma string está em formato SSE válido
     *
     * Retorna true se o formato é válido, false caso contrário
     */
    fun isValidSseFormat(sseString: String): Boolean {
        // Evento SSE deve terminar com linha dupla
        if (!sseString.endsWith("\n\n")) return false

        val lines = sseString.lines()

        for (line in lines) {
            if (line.isEmpty()) continue  // Linhas vazias são OK

            // Comentários começam com ":"
            if (line.startsWith(":")) continue

            // Outras linhas devem ter formato "campo: valor"
            if (!line.contains(":")) return false

            val field = line.substringBefore(":").trim()

            // Campos válidos: id, event, data, retry
            if (field !in listOf("id", "event", "data", "retry", "")) {
                return false
            }
        }

        return true
    }

    /**
     * Parse de string SSE para objeto SseEvent
     *
     * Útil para testes e debugging
     */
    fun parse(sseString: String): SseEvent? {
        if (!isValidSseFormat(sseString)) return null

        var id: String? = null
        var event: String? = null
        var retry: Long? = null
        val dataLines = mutableListOf<String>()
        val comments = mutableListOf<String>()

        for (line in sseString.lines()) {
            when {
                line.isEmpty() -> continue
                line.startsWith(":") -> {
                    comments.add(line.substringAfter(":").trim())
                }
                line.startsWith("id:") -> {
                    id = line.substringAfter(":").trim()
                }
                line.startsWith("event:") -> {
                    event = line.substringAfter(":").trim()
                }
                line.startsWith("retry:") -> {
                    retry = line.substringAfter(":").trim().toLongOrNull()
                }
                line.startsWith("data:") -> {
                    dataLines.add(line.substringAfter(":").trim())
                }
            }
        }

        return SseEvent(
            data = dataLines.joinToString("\n"),
            id = id,
            event = event,
            retry = retry,
            comment = comments.joinToString("\n").takeIf { it.isNotEmpty() }
        )
    }
}

/**
 * Extensão para formatar SseEvent diretamente
 *
 * Uso:
 * ```kotlin
 * val event = SseEvent(data = "Hello", id = "1")
 * val formatted = event.format()
 * ```
 */
fun SseEvent.format(): String = SseFormatter.format(this)

/**
 * Extensão para validar formato SSE
 */
fun String.isValidSSE(): Boolean = SseFormatter.isValidSseFormat(this)

/**
 * Extensão para parsear SSE
 */
fun String.parseSSE(): SseEvent? = SseFormatter.parse(this)
