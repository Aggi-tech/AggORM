# R2DBC Connection Management - Gerenciamento de Conexões Reativas

O AggORM fornece gerenciamento de conexões reativas com pool integrado para R2DBC.

## R2dbcConnectionManager

Gerenciador global singleton de conexões R2DBC com suporte a múltiplas configurações nomeadas.

### Características

- Thread-safe com `ReentrantLock`
- Pool de conexões reativo
- Suporte a múltiplos bancos de dados simultâneos
- Operações suspend não-bloqueantes
- Integração com Kotlin Coroutines

## Configuração

### R2dbcConfig

Classe de configuração para conexões R2DBC:

```kotlin
import com.aggitech.orm.config.R2dbcConfig
import com.aggitech.orm.enums.DatabaseType

val config = R2dbcConfig(
    host = "localhost",
    port = 5432,
    database = "myapp",
    username = "user",
    password = "password",
    databaseType = DatabaseType.POSTGRESQL
)
```

### Registrar Configuração

```kotlin
import com.aggitech.orm.r2dbc.R2dbcConnectionManager

// Configuração padrão
R2dbcConnectionManager.register(config)

// Configuração com nome customizado
R2dbcConnectionManager.register(config, "primary")
```

### Múltiplas Configurações

```kotlin
val primaryDb = R2dbcConfig(
    host = "localhost",
    port = 5432,
    database = "primary",
    username = "user",
    password = "password"
)

val analyticsDb = R2dbcConfig(
    host = "analytics-server",
    port = 5432,
    database = "analytics",
    username = "analytics_user",
    password = "password"
)

// Registrar múltiplos bancos
R2dbcConnectionManager.register(primaryDb, "primary")
R2dbcConnectionManager.register(analyticsDb, "analytics")
```

## R2dbcConnectionPool

Pool de conexões reativo interno.

### Configuração do Pool

```kotlin
val config = R2dbcConfig(
    host = "localhost",
    port = 5432,
    database = "myapp",
    username = "user",
    password = "password",
    poolMinSize = 5,        // Conexões mínimas
    poolMaxSize = 20,       // Conexões máximas
    poolMaxIdleTime = 30    // Tempo máximo de idle (minutos)
)

R2dbcConnectionManager.register(config)
```

### Comportamento do Pool

1. **Conexões Mínimas**: Mantém `poolMinSize` conexões sempre disponíveis
2. **Crescimento Dinâmico**: Cria conexões sob demanda até `poolMaxSize`
3. **Idle Management**: Remove conexões idle após `poolMaxIdleTime`
4. **Validação Automática**: Valida conexões antes de retorná-las

## Uso de Conexões

### getPool() - Obter Pool

```kotlin
import io.r2dbc.pool.ConnectionPool

// Obter pool da configuração padrão
val pool: ConnectionPool = R2dbcConnectionManager.getPool()

// Obter pool de configuração nomeada
val analyticsPool = R2dbcConnectionManager.getPool("analytics")
```

### withConnection() - Suspend Function

Forma recomendada de usar conexões com gerenciamento automático:

```kotlin
import com.aggitech.orm.r2dbc.R2dbcConnectionManager

suspend fun queryUsers() {
    R2dbcConnectionManager.withConnection { connection ->
        // Usar conexão
        val statement = connection.createStatement("SELECT * FROM users")
        val result = statement.execute()

        // Conexão é liberada automaticamente ao final
    }
}
```

### withConnection com Configuração Nomeada

```kotlin
suspend fun queryAnalytics() {
    R2dbcConnectionManager.withConnection("analytics") { connection ->
        // Usa pool do banco analytics
        val statement = connection.createStatement("SELECT * FROM events")
        val result = statement.execute()
    }
}
```

## Integração com Queries

### Uso Automático

As extension functions R2DBC já gerenciam conexões automaticamente:

```kotlin
// Conexão obtida e liberada automaticamente
suspend fun getUsers() = select<User> {
    where { User::active eq true }
}.executeQuery()

// Com configuração nomeada
suspend fun getAnalytics() = select<AnalyticsEvent> {
    where { AnalyticsEvent::timestamp gte today }
}.executeQuery(configName = "analytics")
```

### Reutilizar Conexão

Se precisar executar múltiplas operações na mesma conexão:

```kotlin
suspend fun multipleOperations() {
    R2dbcConnectionManager.withConnection { connection ->
        // Todas as operações compartilham a mesma conexão

        // Operação 1
        val stmt1 = connection.createStatement("INSERT INTO users ...")
        stmt1.execute()

        // Operação 2
        val stmt2 = connection.createStatement("SELECT * FROM users ...")
        stmt2.execute()

        // Conexão liberada automaticamente
    }
}
```

## Transações Reativas

### Transaction Wrapper

```kotlin
suspend fun transferFunds(fromUserId: Long, toUserId: Long, amount: Double) {
    R2dbcConnectionManager.withConnection { connection ->
        try {
            connection.beginTransaction().await()

            // Debitar conta origem
            update<Account> {
                set(Account::balance, Account::balance - amount)
                where { Account::userId eq fromUserId }
            }.executeUpdate()

            // Creditar conta destino
            update<Account> {
                set(Account::balance, Account::balance + amount)
                where { Account::userId eq toUserId }
            }.executeUpdate()

            connection.commitTransaction().await()
        } catch (e: Exception) {
            connection.rollbackTransaction().await()
            throw e
        }
    }
}
```

## Thread Safety

O `R2dbcConnectionManager` é completamente thread-safe e seguro para uso concorrente:

```kotlin
import kotlinx.coroutines.launch

suspend fun concurrentQueries() = coroutineScope {
    // Múltiplas coroutines obtendo conexões simultaneamente
    repeat(100) { i ->
        launch {
            R2dbcConnectionManager.withConnection { connection ->
                // Cada coroutine obtém sua própria conexão do pool
                println("Coroutine $i usando conexão")
            }
        }
    }
}
```

## Bancos de Dados Suportados

### PostgreSQL

```kotlin
val config = R2dbcConfig(
    host = "localhost",
    port = 5432,
    database = "mydb",
    username = "user",
    password = "pass",
    databaseType = DatabaseType.POSTGRESQL
)

R2dbcConnectionManager.register(config)
```

### MySQL

```kotlin
val config = R2dbcConfig(
    host = "localhost",
    port = 3306,
    database = "mydb",
    username = "user",
    password = "pass",
    databaseType = DatabaseType.MYSQL
)

R2dbcConnectionManager.register(config)
```

## Monitoramento do Pool

### Estatísticas Básicas

```kotlin
suspend fun monitorPool() {
    val pool = R2dbcConnectionManager.getPool()

    // Métricas do pool R2DBC
    val metrics = pool.metrics.get()

    println("Acquired connections: ${metrics.acquiredSize()}")
    println("Idle connections: ${metrics.idleSize()}")
    println("Pending acquisitions: ${metrics.pendingAcquireSize()}")
    println("Max allocated: ${metrics.maxAllocatedSize()}")
}
```

### Health Check

```kotlin
suspend fun checkPoolHealth() {
    val pool = R2dbcConnectionManager.getPool()
    val metrics = pool.metrics.get()

    if (metrics.acquiredSize() >= metrics.maxAllocatedSize() * 0.9) {
        logger.warn("R2DBC pool quase esgotado!")
    }

    if (metrics.pendingAcquireSize() > 10) {
        logger.error("Muitas conexões aguardando: ${metrics.pendingAcquireSize()}")
    }
}
```

## Configurações Avançadas

### Connection Factory Customizado

```kotlin
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions

val customFactory: ConnectionFactory = ConnectionFactoryOptions.builder()
    .option(ConnectionFactoryOptions.DRIVER, "postgresql")
    .option(ConnectionFactoryOptions.HOST, "localhost")
    .option(ConnectionFactoryOptions.PORT, 5432)
    .option(ConnectionFactoryOptions.DATABASE, "mydb")
    .option(ConnectionFactoryOptions.USER, "user")
    .option(ConnectionFactoryOptions.PASSWORD, "password")
    // Opções customizadas
    .option(Option.valueOf("sslMode"), "require")
    .option(Option.valueOf("connectTimeout"), Duration.ofSeconds(30))
    .build()
    .let { ConnectionFactories.get(it) }

val config = R2dbcConfig(
    connectionFactory = customFactory,
    poolMinSize = 5,
    poolMaxSize = 20
)

R2dbcConnectionManager.register(config)
```

### Pool Configuration Detalhada

```kotlin
import io.r2dbc.pool.ConnectionPoolConfiguration
import java.time.Duration

val poolConfig = ConnectionPoolConfiguration.builder(connectionFactory)
    .initialSize(5)
    .maxSize(20)
    .maxIdleTime(Duration.ofMinutes(30))
    .maxAcquireTime(Duration.ofSeconds(5))
    .maxCreateConnectionTime(Duration.ofSeconds(10))
    .validationQuery("SELECT 1")
    .build()

val customPool = ConnectionPool(poolConfig)
```

## Cleanup

### Fechar Conexões

```kotlin
// Fechar todas as configurações
suspend fun shutdownPools() {
    R2dbcConnectionManager.closeAll()
}

// Fechar configuração específica
suspend fun shutdownAnalytics() {
    R2dbcConnectionManager.close("analytics")
}
```

## Exemplo Completo

```kotlin
import com.aggitech.orm.config.R2dbcConfig
import com.aggitech.orm.r2dbc.R2dbcConnectionManager
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Configurar banco primário
    val primaryConfig = R2dbcConfig(
        host = "localhost",
        port = 5432,
        database = "primary",
        username = "user",
        password = "password",
        poolMinSize = 5,
        poolMaxSize = 20
    )

    // Configurar banco de analytics
    val analyticsConfig = R2dbcConfig(
        host = "analytics-server",
        port = 5432,
        database = "analytics",
        username = "analytics_user",
        password = "password",
        poolMinSize = 2,
        poolMaxSize = 10
    )

    // Registrar pools
    R2dbcConnectionManager.register(primaryConfig, "primary")
    R2dbcConnectionManager.register(analyticsConfig, "analytics")

    // Usar conexões
    val users = select<User> {
        where { User::active eq true }
    }.executeQuery(configName = "primary")

    val events = select<AnalyticsEvent> {
        where { AnalyticsEvent::timestamp gte today }
    }.executeQuery(configName = "analytics")

    // Monitorar pools
    monitorPool()

    // Cleanup ao final
    R2dbcConnectionManager.closeAll()
}
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Usar withConnection para gerenciamento automático
suspend fun query() {
    R2dbcConnectionManager.withConnection { connection ->
        // Código aqui
    }
}

// Configurar tamanhos de pool apropriados
R2dbcConfig(
    poolMinSize = 5,    // Mínimo razoável
    poolMaxSize = 20    // Máximo baseado na carga esperada
)

// Monitorar saúde do pool
suspend fun healthCheck() {
    checkPoolHealth()
}
```

### [AVOID] Evitar

```kotlin
// Não criar pools excessivamente grandes
R2dbcConfig(
    poolMinSize = 100,   // [AVOID] Muito alto
    poolMaxSize = 500    // [AVOID] Excessivo
)

// Não esquecer de fechar pools ao shutdown
// [AVOID] Sem cleanup

// Não usar runBlocking em código de produção
fun query() = runBlocking {  // [AVOID] Bloqueia thread
    R2dbcConnectionManager.withConnection { }
}
```

## Troubleshooting

### Pool Esgotado

```kotlin
// Sintoma: Timeout ao adquirir conexão
// Solução: Aumentar poolMaxSize ou investigar vazamento de conexões

suspend fun diagnoseLeak() {
    val metrics = R2dbcConnectionManager.getPool().metrics.get()

    if (metrics.acquiredSize() >= metrics.maxAllocatedSize()) {
        logger.error("Pool esgotado! Verificar vazamento de conexões.")
        logger.error("Acquired: ${metrics.acquiredSize()}")
        logger.error("Idle: ${metrics.idleSize()}")
    }
}
```

### Conexões Lentas

```kotlin
// Sintoma: Queries lentas
// Solução: Ajustar timeouts

val config = R2dbcConfig(
    // ... outras configs
    maxAcquireTime = Duration.ofSeconds(10),  // Aumentar timeout
    maxCreateConnectionTime = Duration.ofSeconds(20)
)
```
