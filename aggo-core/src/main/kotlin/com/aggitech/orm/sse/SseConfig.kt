package com.aggitech.orm.sse

import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Configuração para streaming SSE
 *
 * Uso básico:
 * ```kotlin
 * val config = SseConfig() // Usa valores padrão
 * ```
 *
 * Customizado:
 * ```kotlin
 * val config = SseConfig(
 *     heartbeatInterval = Duration.ofSeconds(30),
 *     enableHeartbeat = true,
 *     retryInterval = 5000,
 *     bufferSize = 512
 * )
 * ```
 *
 * Com builder:
 * ```kotlin
 * val config = sseConfig {
 *     heartbeatInterval = Duration.ofSeconds(20)
 *     retryInterval = 2000
 *     eventIdGenerator = { UUID.randomUUID().toString() }
 * }
 * ```
 */
data class SseConfig(
    /**
     * Intervalo entre mensagens de heartbeat (keep-alive)
     * Padrão: 15 segundos
     *
     * Heartbeats mantêm a conexão HTTP ativa e ajudam a detectar
     * desconexões mais rapidamente.
     */
    val heartbeatInterval: Duration = Duration.ofSeconds(15),

    /**
     * Habilita ou desabilita heartbeat automático
     * Padrão: true
     *
     * Quando habilitado, eventos de heartbeat (comentários) são
     * intercalados automaticamente no stream.
     */
    val enableHeartbeat: Boolean = true,

    /**
     * Tempo em milissegundos que o cliente deve aguardar antes de reconectar
     * Padrão: 3000ms (3 segundos)
     *
     * Este valor é enviado no campo "retry:" dos eventos SSE e sobrescreve
     * o comportamento padrão do navegador.
     */
    val retryInterval: Long = 3000,

    /**
     * Tamanho do buffer para streaming
     * Padrão: 256
     *
     * Controla quantos eventos podem ser enfileirados antes de aplicar backpressure.
     * Valores maiores usam mais memória mas reduzem chance de perda de eventos.
     */
    val bufferSize: Int = 256,

    /**
     * Gerador de IDs para eventos
     * Padrão: UUID aleatório
     *
     * Pode ser customizado para usar IDs sequenciais, timestamps, etc.
     * Os IDs devem ser únicos e monotônicos para permitir retomada correta.
     */
    val eventIdGenerator: () -> String = { UUID.randomUUID().toString() },

    /**
     * Tipo de evento padrão
     * Padrão: "message"
     *
     * Usado quando nenhum tipo específico é fornecido.
     * O cliente pode filtrar eventos baseado neste campo.
     */
    val defaultEventType: String = "message",

    /**
     * Formato de serialização para dados
     * Padrão: JSON
     *
     * Controla como objetos/maps são convertidos para string no campo data.
     */
    val serializationFormat: SerializationFormat = SerializationFormat.JSON,

    /**
     * Habilita compressão de dados grandes
     * Padrão: false
     *
     * Quando true, dados maiores que `compressionThreshold` são comprimidos.
     */
    val enableCompression: Boolean = false,

    /**
     * Tamanho mínimo em bytes para aplicar compressão
     * Padrão: 1024 bytes (1KB)
     */
    val compressionThreshold: Int = 1024
) {

    /**
     * Builder fluente para SseConfig
     */
    class Builder {
        var heartbeatInterval: Duration = Duration.ofSeconds(15)
        var enableHeartbeat: Boolean = true
        var retryInterval: Long = 3000
        var bufferSize: Int = 256
        var eventIdGenerator: () -> String = { UUID.randomUUID().toString() }
        var defaultEventType: String = "message"
        var serializationFormat: SerializationFormat = SerializationFormat.JSON
        var enableCompression: Boolean = false
        var compressionThreshold: Int = 1024

        fun build(): SseConfig = SseConfig(
            heartbeatInterval = heartbeatInterval,
            enableHeartbeat = enableHeartbeat,
            retryInterval = retryInterval,
            bufferSize = bufferSize,
            eventIdGenerator = eventIdGenerator,
            defaultEventType = defaultEventType,
            serializationFormat = serializationFormat,
            enableCompression = enableCompression,
            compressionThreshold = compressionThreshold
        )
    }

    companion object {
        /**
         * Configuração otimizada para streaming de alta frequência
         * - Heartbeat mais frequente (5s)
         * - Buffer maior (512)
         * - Retry mais rápido (1s)
         */
        fun highFrequency(): SseConfig = SseConfig(
            heartbeatInterval = Duration.ofSeconds(5),
            enableHeartbeat = true,
            retryInterval = 1000,
            bufferSize = 512
        )

        /**
         * Configuração otimizada para streaming de baixa frequência
         * - Heartbeat menos frequente (30s)
         * - Buffer menor (64)
         * - Retry mais lento (5s)
         */
        fun lowFrequency(): SseConfig = SseConfig(
            heartbeatInterval = Duration.ofSeconds(30),
            enableHeartbeat = true,
            retryInterval = 5000,
            bufferSize = 64
        )

        /**
         * Configuração sem heartbeat
         * - Útil quando o cliente gerencia keep-alive
         */
        fun noHeartbeat(): SseConfig = SseConfig(
            enableHeartbeat = false
        )

        /**
         * Gerador de IDs sequenciais (útil para debugging e ordenação)
         */
        fun sequentialIdGenerator(): () -> String {
            val counter = AtomicLong(0)
            return { counter.incrementAndGet().toString() }
        }

        /**
         * Gerador de IDs baseado em timestamp
         */
        fun timestampIdGenerator(): () -> String {
            return { System.currentTimeMillis().toString() }
        }
    }
}

/**
 * Formato de serialização para dados SSE
 */
enum class SerializationFormat {
    /**
     * Serialização JSON (padrão)
     */
    JSON,

    /**
     * Serialização como string simples
     */
    PLAIN_TEXT,

    /**
     * Serialização customizada (requer implementação do usuário)
     */
    CUSTOM
}

/**
 * Função DSL para criar SseConfig com builder
 *
 * Uso:
 * ```kotlin
 * val config = sseConfig {
 *     heartbeatInterval = Duration.ofSeconds(10)
 *     retryInterval = 2000
 *     bufferSize = 128
 * }
 * ```
 */
fun sseConfig(builder: SseConfig.Builder.() -> Unit): SseConfig {
    return SseConfig.Builder().apply(builder).build()
}
