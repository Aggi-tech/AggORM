# Query Logging - Log de Queries SQL

O AggORM fornece um sistema plugável de logging para queries SQL executadas.

## QueryLogger Interface

Interface que define o contrato para implementações de logging:

```kotlin
interface QueryLogger {
    fun log(
        level: LogLevel,
        sql: String,
        params: List<Any?> = emptyList(),
        executionTimeMs: Long? = null,
        error: Throwable? = null
    )
}
```

## Implementações Prontas

### ConsoleQueryLogger

Logger padrão que imprime logs no console:

```kotlin
import com.aggitech.orm.logging.ConsoleQueryLogger
import com.aggitech.orm.logging.LogLevel
import com.aggitech.orm.config.DbConfig

val config = DbConfig(
    // ... outras configurações
    logger = ConsoleQueryLogger(
        minLevel = LogLevel.DEBUG,
        showParameters = true
    )
)

JdbcConnectionManager.register(config)
```

Saída de exemplo:

```
[DEBUG] SQL: SELECT * FROM users WHERE age >= ? AND email LIKE ?
        Parameters: [18, %@example.com]
        Execution time: 15ms

[INFO] SQL: INSERT INTO users (name, email, age) VALUES (?, ?, ?)
       Parameters: [John Doe, john@example.com, 25]
       Execution time: 8ms

[ERROR] SQL: SELECT * FROM invalid_table
        Error: Table 'invalid_table' doesn't exist
```

### NoOpQueryLogger

Logger que não faz nada (para desabilitar logging):

```kotlin
import com.aggitech.orm.logging.NoOpQueryLogger

val config = DbConfig(
    // ... outras configurações
    logger = NoOpQueryLogger()
)
```

## Log Levels

Níveis de log disponíveis:

```kotlin
enum class LogLevel {
    TRACE,   // Logs muito detalhados
    DEBUG,   // Informações de debug (padrão para queries)
    INFO,    // Informações gerais
    WARN,    // Avisos
    ERROR,   // Erros
    OFF      // Desabilitar logging
}
```

### Configuração de Nível Mínimo

```kotlin
// Log apenas INFO e acima (INFO, WARN, ERROR)
val logger = ConsoleQueryLogger(
    minLevel = LogLevel.INFO,
    showParameters = true
)

// Log tudo (TRACE e acima)
val verboseLogger = ConsoleQueryLogger(
    minLevel = LogLevel.TRACE,
    showParameters = true
)

// Desabilitar completamente
val noLogger = ConsoleQueryLogger(
    minLevel = LogLevel.OFF
)
```

## Configuração de Parâmetros

### Mostrar ou Ocultar Parâmetros

```kotlin
// Mostrar parâmetros (útil para debug)
val debugLogger = ConsoleQueryLogger(
    minLevel = LogLevel.DEBUG,
    showParameters = true
)
// Output: SQL: SELECT * FROM users WHERE id = ?
//         Parameters: [1]

// Ocultar parâmetros (útil em produção por segurança)
val productionLogger = ConsoleQueryLogger(
    minLevel = LogLevel.INFO,
    showParameters = false
)
// Output: SQL: SELECT * FROM users WHERE id = ?
```

## Logger Global vs Por Configuração

### Logger Global

Define um logger padrão para todas as configurações:

```kotlin
import com.aggitech.orm.logging.QueryLogger

// Definir logger global
QueryLogger.setGlobalLogger(
    ConsoleQueryLogger(
        minLevel = LogLevel.DEBUG,
        showParameters = true
    )
)

// Todas as conexões usarão este logger por padrão
JdbcConnectionManager.register(config1)
JdbcConnectionManager.register(config2, "secondary")
```

### Logger Por Configuração

Define loggers diferentes para cada configuração:

```kotlin
val primaryLogger = ConsoleQueryLogger(
    minLevel = LogLevel.DEBUG,
    showParameters = true
)

val analyticsLogger = ConsoleQueryLogger(
    minLevel = LogLevel.INFO,
    showParameters = false
)

JdbcConnectionManager.register(
    DbConfig(/* ... */, logger = primaryLogger)
)

JdbcConnectionManager.register(
    DbConfig(/* ... */, logger = analyticsLogger),
    "analytics"
)
```

## Logging Automático

### SELECT Queries

```kotlin
val users = select<User> {
    where { User::age gte 18 }
    limit(10)
}.execute()

// Log automático:
// [DEBUG] SQL: SELECT * FROM users WHERE age >= ? LIMIT ?
//         Parameters: [18, 10]
//         Execution time: 12ms
```

### INSERT Queries

```kotlin
val user = User(name = "John", email = "john@example.com")
insert(user).execute()

// Log automático:
// [DEBUG] SQL: INSERT INTO users (name, email) VALUES (?, ?)
//         Parameters: [John, john@example.com]
//         Execution time: 8ms
```

### UPDATE Queries

```kotlin
update<User> {
    set(User::name, "John Updated")
    where { User::id eq 1L }
}.execute()

// Log automático:
// [DEBUG] SQL: UPDATE users SET name = ? WHERE id = ?
//         Parameters: [John Updated, 1]
//         Execution time: 5ms
```

### DELETE Queries

```kotlin
delete<User> {
    where { User::id eq 1L }
}.execute()

// Log automático:
// [DEBUG] SQL: DELETE FROM users WHERE id = ?
//         Parameters: [1]
//         Execution time: 4ms
```

### Erros

```kotlin
try {
    select<User> {
        where { User::invalidField eq "value" }
    }.execute()
} catch (e: Exception) {
    // Log automático de erro
}

// Log automático:
// [ERROR] SQL: SELECT * FROM users WHERE invalid_field = ?
//         Error: Column 'invalid_field' not found
```

## Logger Customizado

### Implementação Própria

```kotlin
class CustomLogger : QueryLogger {
    override fun log(
        level: LogLevel,
        sql: String,
        params: List<Any?>,
        executionTimeMs: Long?,
        error: Throwable?
    ) {
        // Sua lógica customizada
        when (level) {
            LogLevel.ERROR -> {
                // Enviar para sistema de monitoramento
                sendToMonitoring(sql, error)
            }
            LogLevel.WARN -> {
                // Registrar warning
                logger.warn(sql)
            }
            else -> {
                // Log normal
                logger.debug(sql)
            }
        }
    }
}

// Usar logger customizado
val config = DbConfig(
    // ... outras configurações
    logger = CustomLogger()
)
```

### Logger com SLF4J

```kotlin
import org.slf4j.LoggerFactory

class Slf4jQueryLogger(
    private val minLevel: LogLevel = LogLevel.DEBUG,
    private val showParameters: Boolean = true
) : QueryLogger {

    private val logger = LoggerFactory.getLogger("AggORM")

    override fun log(
        level: LogLevel,
        sql: String,
        params: List<Any?>,
        executionTimeMs: Long?,
        error: Throwable?
    ) {
        if (level < minLevel) return

        val message = buildString {
            append("SQL: $sql")
            if (showParameters && params.isNotEmpty()) {
                append("\nParameters: $params")
            }
            if (executionTimeMs != null) {
                append("\nExecution time: ${executionTimeMs}ms")
            }
        }

        when (level) {
            LogLevel.TRACE -> logger.trace(message, error)
            LogLevel.DEBUG -> logger.debug(message, error)
            LogLevel.INFO -> logger.info(message, error)
            LogLevel.WARN -> logger.warn(message, error)
            LogLevel.ERROR -> logger.error(message, error)
            LogLevel.OFF -> {}
        }
    }
}
```

### Logger com Métricas

```kotlin
class MetricsQueryLogger(
    private val metricsCollector: MetricsCollector,
    private val delegate: QueryLogger = ConsoleQueryLogger()
) : QueryLogger {

    override fun log(
        level: LogLevel,
        sql: String,
        params: List<Any?>,
        executionTimeMs: Long?,
        error: Throwable?
    ) {
        // Coletar métricas
        if (executionTimeMs != null) {
            metricsCollector.recordQueryTime(executionTimeMs)
        }

        if (error != null) {
            metricsCollector.incrementErrorCount()
        }

        // Delegar para outro logger
        delegate.log(level, sql, params, executionTimeMs, error)
    }
}
```

## Monitoramento de Performance

### Detectar Queries Lentas

```kotlin
class SlowQueryLogger(
    private val slowThresholdMs: Long = 1000,
    private val delegate: QueryLogger = ConsoleQueryLogger()
) : QueryLogger {

    override fun log(
        level: LogLevel,
        sql: String,
        params: List<Any?>,
        executionTimeMs: Long?,
        error: Throwable?
    ) {
        // Log em nível WARN se query for lenta
        val logLevel = if (executionTimeMs != null && executionTimeMs > slowThresholdMs) {
            LogLevel.WARN
        } else {
            level
        }

        delegate.log(logLevel, sql, params, executionTimeMs, error)
    }
}

// Uso
val config = DbConfig(
    // ... outras configurações
    logger = SlowQueryLogger(slowThresholdMs = 500)
)
```

### Análise de Queries

```kotlin
class QueryAnalyzer : QueryLogger {
    private val queryStats = mutableMapOf<String, QueryStat>()

    data class QueryStat(
        var count: Int = 0,
        var totalTimeMs: Long = 0,
        var avgTimeMs: Double = 0.0,
        var maxTimeMs: Long = 0
    )

    override fun log(
        level: LogLevel,
        sql: String,
        params: List<Any?>,
        executionTimeMs: Long?,
        error: Throwable?
    ) {
        if (executionTimeMs != null) {
            val stat = queryStats.getOrPut(sql) { QueryStat() }
            stat.count++
            stat.totalTimeMs += executionTimeMs
            stat.avgTimeMs = stat.totalTimeMs.toDouble() / stat.count
            stat.maxTimeMs = maxOf(stat.maxTimeMs, executionTimeMs)
        }
    }

    fun printStats() {
        println("Query Statistics:")
        queryStats.forEach { (sql, stat) ->
            println("$sql")
            println("  Count: ${stat.count}")
            println("  Avg time: ${stat.avgTimeMs}ms")
            println("  Max time: ${stat.maxTimeMs}ms")
        }
    }
}
```

## Boas Práticas

### [OK] Desenvolvimento

```kotlin
// Logging verboso em desenvolvimento
val devConfig = DbConfig(
    // ... outras configurações
    logger = ConsoleQueryLogger(
        minLevel = LogLevel.DEBUG,
        showParameters = true
    )
)
```

### [OK] Produção

```kotlin
// Logging mínimo em produção, sem parâmetros sensíveis
val prodConfig = DbConfig(
    // ... outras configurações
    logger = Slf4jQueryLogger(
        minLevel = LogLevel.WARN,
        showParameters = false  // Não expor dados sensíveis
    )
)
```

### [OK] Monitoramento

```kotlin
// Combinar logging com métricas
val monitoredConfig = DbConfig(
    // ... outras configurações
    logger = MetricsQueryLogger(
        metricsCollector = prometheus,
        delegate = Slf4jQueryLogger()
    )
)
```

### [AVOID] Evitar

```kotlin
// Não logar parâmetros sensíveis em produção
val badConfig = DbConfig(
    logger = ConsoleQueryLogger(
        showParameters = true  // [AVOID] Pode expor senhas, tokens, etc.
    )
)

// Não usar logging muito verboso em produção
val verboseConfig = DbConfig(
    logger = ConsoleQueryLogger(
        minLevel = LogLevel.TRACE  // [AVOID] Overhead desnecessário
    )
)
```

## Exemplo Completo

```kotlin
// Ambiente de desenvolvimento
val developmentLogger = ConsoleQueryLogger(
    minLevel = LogLevel.DEBUG,
    showParameters = true
)

// Ambiente de produção com SLF4J
val productionLogger = Slf4jQueryLogger(
    minLevel = LogLevel.INFO,
    showParameters = false
)

// Ambiente com análise de performance
val performanceLogger = SlowQueryLogger(
    slowThresholdMs = 1000,
    delegate = productionLogger
)

// Configurar de acordo com o ambiente
val logger = when (System.getenv("ENV")) {
    "development" -> developmentLogger
    "production" -> performanceLogger
    else -> NoOpQueryLogger()
}

val config = DbConfig(
    host = "localhost",
    database = "mydb",
    username = "user",
    password = "password",
    logger = logger
)

JdbcConnectionManager.register(config)
```
