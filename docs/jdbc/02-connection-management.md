# Connection Management - Gerenciamento de Conexões JDBC

O AggORM fornece um sistema robusto de gerenciamento de conexões com pool integrado.

## JdbcConnectionManager

Gerenciador global singleton de conexões JDBC com suporte a múltiplas configurações nomeadas.

### Características

- Thread-safe com `ReentrantLock`
- Pool de conexões configurável
- Suporte a múltiplos bancos de dados simultâneos
- Validação e reciclagem de conexões
- Estatísticas de uso do pool

## Configuração

### Configuração Básica

```kotlin
import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.jdbc.JdbcConnectionManager

// Configuração padrão
val config = DbConfig(
    host = "localhost",
    port = 5432,
    database = "myapp",
    username = "user",
    password = "password",
    databaseType = DatabaseType.POSTGRESQL
)

// Registrar configuração
JdbcConnectionManager.register(config)
```

### Configuração com Nome Customizado

```kotlin
// Múltiplas configurações para diferentes bancos
val primaryDb = DbConfig(
    host = "localhost",
    port = 5432,
    database = "primary",
    username = "user",
    password = "password"
)

val analyticsDb = DbConfig(
    host = "analytics-server",
    port = 5432,
    database = "analytics",
    username = "analytics_user",
    password = "password"
)

// Registrar com nomes
JdbcConnectionManager.register(primaryDb, "primary")
JdbcConnectionManager.register(analyticsDb, "analytics")
```

### Configuração do Pool

```kotlin
val config = DbConfig(
    host = "localhost",
    port = 5432,
    database = "myapp",
    username = "user",
    password = "password",
    poolMinSize = 5,        // Mínimo de conexões no pool
    poolMaxSize = 20        // Máximo de conexões no pool
)

JdbcConnectionManager.register(config)
```

## Uso de Conexões

### Obter e Liberar Conexão Manualmente

```kotlin
// Obter conexão do pool
val connection = JdbcConnectionManager.getConnection()

try {
    // Usar a conexão
    val stmt = connection.prepareStatement("SELECT * FROM users")
    val rs = stmt.executeQuery()
    // ...
} finally {
    // Sempre liberar a conexão de volta ao pool
    JdbcConnectionManager.releaseConnection(connection)
}
```

### Usando withConnection (Recomendado)

```kotlin
// Forma mais segura - gerencia automaticamente o ciclo de vida
JdbcConnectionManager.withConnection { connection ->
    val stmt = connection.prepareStatement("SELECT * FROM users")
    val rs = stmt.executeQuery()
    // ...
    // Conexão é liberada automaticamente
}

// Com configuração nomeada
JdbcConnectionManager.withConnection("analytics") { connection ->
    // Usa a conexão do banco analytics
}
```

### Uso Direto com Queries

```kotlin
// As extension functions já gerenciam conexões automaticamente
val users = select<User> {
    where { User::active eq true }
}.execute()  // Obtém e libera conexão automaticamente

// Especificar configuração
val analytics = select<AnalyticsEvent> {
    where { AnalyticsEvent::timestamp gte today }
}.execute(configName = "analytics")
```

## Pool de Conexões

### JdbcConnectionPool

Pool interno que gerencia as conexões de forma eficiente.

```kotlin
// As configurações de pool são definidas no DbConfig
val config = DbConfig(
    // ... outras configurações
    poolMinSize = 5,    // Conexões sempre disponíveis
    poolMaxSize = 20    // Máximo sob carga
)
```

### Comportamento do Pool

1. **Conexões Mínimas**: Mantém sempre `poolMinSize` conexões abertas
2. **Crescimento**: Cria novas conexões sob demanda até `poolMaxSize`
3. **Validação**: Valida conexões antes de retorná-las
4. **Reciclagem**: Remove conexões inválidas e cria novas

### Estatísticas do Pool

```kotlin
// Obter estatísticas de uso
val stats = JdbcConnectionManager.getStats()

println("Conexões ativas: ${stats.activeConnections}")
println("Conexões disponíveis: ${stats.availableConnections}")
println("Total de conexões: ${stats.totalConnections}")

// Com configuração nomeada
val analyticsStats = JdbcConnectionManager.getStats("analytics")
```

## Transações

### Transaction Wrapper

```kotlin
import com.aggitech.orm.jdbc.transaction

// Executa bloco em uma transação
transaction { connection ->
    // Todas as operações neste bloco compartilham a mesma conexão

    insert(User(name = "John", email = "john@example.com")).execute()

    insert(Profile(userId = 1L, bio = "...")).execute()

    // Commit automático ao final
    // Rollback automático em caso de exceção
}

// Com configuração nomeada
transaction("analytics") { connection ->
    // Transação no banco analytics
}
```

### Controle Manual de Transação

```kotlin
JdbcConnectionManager.withConnection { connection ->
    try {
        connection.autoCommit = false

        // Operações
        val stmt1 = connection.prepareStatement("INSERT INTO users ...")
        stmt1.executeUpdate()

        val stmt2 = connection.prepareStatement("INSERT INTO profiles ...")
        stmt2.executeUpdate()

        connection.commit()
    } catch (e: Exception) {
        connection.rollback()
        throw e
    }
}
```

## Thread Safety

O `JdbcConnectionManager` é completamente thread-safe:

```kotlin
// Múltiplas threads podem obter conexões simultaneamente
val threads = (1..100).map { threadId ->
    thread {
        JdbcConnectionManager.withConnection { connection ->
            // Cada thread obtém sua própria conexão do pool
            println("Thread $threadId usando conexão")
        }
    }
}

threads.forEach { it.join() }
```

## Bancos de Dados Suportados

### PostgreSQL

```kotlin
val config = DbConfig(
    host = "localhost",
    port = 5432,
    database = "mydb",
    username = "user",
    password = "pass",
    databaseType = DatabaseType.POSTGRESQL
)
```

### MySQL

```kotlin
val config = DbConfig(
    host = "localhost",
    port = 3306,
    database = "mydb",
    username = "user",
    password = "pass",
    databaseType = DatabaseType.MYSQL
)
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Usar withConnection para gerenciamento automático
JdbcConnectionManager.withConnection { connection ->
    // Código aqui
}

// Usar extension functions que gerenciam conexões
select<User> { }.execute()

// Definir tamanhos de pool apropriados
DbConfig(poolMinSize = 5, poolMaxSize = 20)
```

### [AVOID] Evitar

```kotlin
// Não esquecer de liberar conexões
val conn = JdbcConnectionManager.getConnection()
// ... uso
// Esqueceu de chamar releaseConnection() - vazamento de conexão!

// Não criar pools muito grandes desnecessariamente
DbConfig(poolMinSize = 100, poolMaxSize = 500)  // Provavelmente excessivo

// Não criar novas configurações repetidamente
repeat(100) {
    JdbcConnectionManager.register(config)  // Registrar uma vez é suficiente
}
```

## Cleanup

```kotlin
// Fechar todas as conexões (geralmente ao shutdown da aplicação)
JdbcConnectionManager.close()

// Fechar conexões de uma configuração específica
JdbcConnectionManager.close("analytics")
```

## Monitoramento

```kotlin
// Monitorar saúde do pool
fun monitorPool() {
    val stats = JdbcConnectionManager.getStats()

    if (stats.activeConnections >= stats.totalConnections * 0.9) {
        logger.warn("Pool quase esgotado: ${stats.activeConnections}/${stats.totalConnections}")
    }

    if (stats.availableConnections == 0) {
        logger.error("Sem conexões disponíveis no pool!")
    }
}
```
