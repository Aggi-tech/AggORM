# AggORM - Kotlin ORM Declarativo

**AggORM** é um ORM leve, type-safe e declarativo para Kotlin que combina a performance do JDBC com a elegância de uma DSL expressiva.

## Características Principais

- **DSL Declarativa**: Sintaxe expressiva e legível inspirada em query builders modernos
- **Type-Safe**: Totalmente tipado usando Kotlin Reflection e Property References
- **JDBC**: Baseado em JDBC para máxima compatibilidade e performance
- **SQL Injection Protection**: Prepared Statements automáticos em todas as operações
- **Validação de Entidades**: Sistema de validação integrado com annotations
- **Funções de Agregação**: Suporte a COUNT, SUM, AVG, MIN, MAX
- **Spring Boot Integration**: Starter para integração fácil com Spring Boot

## Novas Funcionalidades v1.1.0 ✨

- **Connection Manager**: Pool de conexões automático e thread-safe (JDBC e R2DBC)
- **Execução Ergonômica**: Não é mais necessário passar `Connection` ou `SqlDialect` manualmente
- **Mapeamento Automático**: Converte resultados para entidades tipadas automaticamente
- **Logging Integrado**: Sistema de logging plugável com suporte a console e níveis configuráveis
- **Transações Simplificadas**: API limpa para transações com commit/rollback automático
- **R2DBC Thread-Safe**: Pool reativo otimizado para Coroutines com `Dispatchers.IO`
- **Entity Mapping**: Extensões `executeAsEntities()`, `executeAsEntityOrNull()`, `executeAsEntityFlow()`
- **Streaming com Flow**: Processamento sob demanda de grandes volumes de dados

## Instalação

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Core module
    implementation("com.github.Aggi-tech.AggORM:aggo-core:1.1.0")
    implementation(kotlin("reflect"))

    // Driver JDBC (escolha o seu banco)
    implementation("org.postgresql:postgresql:42.7.1") // PostgreSQL
    // ou
    implementation("com.mysql:mysql-connector-j:8.0.33") // MySQL
}

// Para Spring Boot
dependencies {
    implementation("com.github.Aggi-tech.AggORM:aggo-spring-boot-starter:1.1.0")
}
```

### Gradle (Groovy)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Aggi-tech.AggORM:aggo-core:1.1.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.Aggi-tech.AggORM</groupId>
    <artifactId>aggo-core</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Quick Start (v1.1.0)

### JDBC

```kotlin
// 1. Configuração inicial (uma vez no início da aplicação)
val config = DbConfig(
    database = "myapp",
    host = "localhost",
    port = 5432,
    user = "postgres",
    password = "password",
    type = SupportedDatabases.POSTGRESQL
)

JdbcConnectionManager.register(config = config)
QueryLoggerManager.enableConsoleLogging() // Opcional

// 2. Use em qualquer lugar - sem passar Connection/Dialect!
val users: List<User> = select<User> {
    where { User::age gte 18 }
}.executeAsEntities()

// 3. Transações simplificadas
transaction {
    insert<User> { ... }.execute()
    insert<Order> { ... }.execute()
    // Commit automático ao sair do bloco
}
```

### R2DBC (Reativo)

```kotlin
// 1. Configuração inicial
val config = R2dbcConfig(
    database = "myapp",
    host = "localhost",
    port = 5432,
    user = "postgres",
    password = "password",
    type = SupportedDatabases.POSTGRESQL
)

R2dbcConnectionManager.register(config = config)

// 2. Use com suspend functions
suspend fun getUsers(): List<User> {
    return select<User> {
        where { User::age gte 18 }
    }.executeAsEntities()
}

// 3. Streaming com Flow
select<User> { ... }.executeAsEntityFlow()
    .collect { user -> processUser(user) }
```

## Configuração (Forma Tradicional)

```kotlin
val config = DbConfig(
    database = "myapp",
    host = "localhost",
    port = 5432,
    user = "postgres",
    password = "password",
    type = SupportedDatabases.POSTGRESQL
)

val connectionFactory = JdbcConnectionFactory(config)
```

## Uso da DSL

### SELECT Queries

```kotlin
// Query básica
val query = select<User> {
    select {
        +User::name
        +User::email
        +User::age
    }
    where {
        User::age gte 18
    }
    orderBy {
        User::name.asc()
    }
    limit(10)
}

// Renderizar SQL
val renderer = SelectRenderer(PostgresDialect)
val rendered = renderer.render(query)
println("SQL: ${rendered.sql}")
println("Parameters: ${rendered.parameters}")

// Query com WHERE complexo
val adults = select<User> {
    where {
        ((User::age gte 18) and (User::age lte 65)) and
            (User::email like "%@gmail.com")
    }
    orderBy {
        User::age.desc()
    }
}

// Paginação
val paged = select<User> {
    limit(10)
    offset(20)
}
```

### Funções de Agregação

```kotlin
// COUNT, SUM, AVG, MIN, MAX
val aggregateQuery = select<User> {
    select {
        countAll("total_users")
        avg(User::age, "average_age")
        min(User::age, "min_age")
        max(User::age, "max_age")
    }
}

// GROUP BY e HAVING
val groupByQuery = select<Order> {
    select {
        +Order::userId
        sum(Order::totalAmount, "total_spent")
        countAll("order_count")
    }
    groupBy(Order::userId)
    having {
        Order::totalAmount gt 1000.0
    }
    orderBy {
        Order::totalAmount.desc()
    }
}
```

### Operadores WHERE Suportados

```kotlin
where {
    User::age eq 18                      // =
    User::age ne 18                      // !=
    User::age gt 18                      // >
    User::age gte 18                     // >=
    User::age lt 18                      // <
    User::age lte 18                     // <=
    User::email like "%@gmail.com"       // LIKE
    User::email notLike "%@spam.com"     // NOT LIKE
    User::id inList listOf(1, 2, 3)      // IN
    User::id notInList listOf(4, 5)      // NOT IN
    User::email.isNull()                 // IS NULL
    User::email.isNotNull()              // IS NOT NULL
    User::age.between(18, 65)            // BETWEEN

    // Operadores lógicos
    (User::age gte 18) and (User::age lte 65)
    (User::cityId eq 1L) or (User::cityId eq 2L)
    not(User::email like "%@spam.com")
}
```

### INSERT Operations

```kotlin
// INSERT com valores explícitos
val insertQuery = insert<User> {
    User::name to "John Doe"
    User::email to "john@example.com"
    User::age to 30
    User::cityId to 1L
}

val renderer = InsertRenderer(PostgresDialect)
val rendered = renderer.render(insertQuery)

// INSERT usando uma entidade
val user = User(
    name = "Jane Smith",
    email = "jane@example.com",
    age = 28,
    cityId = 2L
)
val entityInsert = insert(user)
```

### UPDATE Operations

```kotlin
val updateQuery = update<User> {
    User::name to "John Updated"
    User::age to 31
    where {
        User::id eq 1L
    }
}

val renderer = UpdateRenderer(PostgresDialect)
val rendered = renderer.render(updateQuery)

// Update condicional
val conditionalUpdate = update<User> {
    User::cityId to null
    where {
        (User::age lt 18) or (User::age gt 100)
    }
}
```

### DELETE Operations

```kotlin
val deleteQuery = delete<User> {
    where {
        User::id eq 999L
    }
}

val renderer = DeleteRenderer(PostgresDialect)
val rendered = renderer.render(deleteQuery)

// Delete condicional
val conditionalDelete = delete<User> {
    where {
        (User::age lt 18) and (User::email like "%@temporary.com")
    }
}
```

## Definindo Entidades

```kotlin
data class User(
    val id: Long? = null,      // ID opcional para novos registros
    val name: String,
    val email: String,
    val age: Int,
    val cityId: Long? = null
)

data class City(
    val id: Long? = null,
    val name: String,
    val country: String
)
```

**Convenções:**
- Nome da classe em lowercase = nome da tabela
- Propriedades = nomes das colunas
- Campo `id` é tratado especialmente em INSERTs

## Validação de Entidades

```kotlin
// Usando o sistema de validação
val validUser = User(
    id = 1L,
    name = "Alice Johnson",
    email = "alice@example.com",
    age = 25,
    cityId = 1L
)

val validResult = validUser.validate()
println("Valid user: ${validResult.isValid}")

val invalidUser = User(
    id = 2L,
    name = "B",                    // Muito curto
    email = "invalid-email",       // Email inválido
    age = 15,                      // Menor de idade
    cityId = 1L
)

val invalidResult = invalidUser.validate()
println("Invalid user: ${invalidResult.isValid}")
invalidResult.errors.forEach { error ->
    println("  ${error.property}: ${error.message}")
}
```

## Resolução de Nomes (Snake Case)

```kotlin
// O AggORM converte automaticamente nomes de classes e propriedades para snake_case
println("User -> ${EntityRegistry.resolveTable(User::class)}")         // "user"
println("City -> ${EntityRegistry.resolveTable(City::class)}")         // "city"
println("User::cityId -> ${EntityRegistry.resolveColumn(User::cityId)}") // "city_id"
println("User::name -> ${EntityRegistry.resolveColumn(User::name)}")     // "name"
```

## SELECT DISTINCT

```kotlin
val distinctQuery = select<User> {
    select {
        distinct()
        +User::email
    }
}
```

## Boas Práticas

### 1. Use Prepared Statements (Automático)
O AggORM usa prepared statements automaticamente para proteger contra SQL injection:

```kotlin
// ✅ Seguro
where {
    User::email eq userInput  // Usa prepared statement
}

// ❌ Evite raw SQL com dados não validados
where {
    raw("email = '$userInput'")  // Vulnerável
}
```

### 2. Use Connection Pooling em Produção

```kotlin
// Para produção, use HikariCP
val hikariConfig = HikariConfig().apply {
    jdbcUrl = config.url
    username = config.user
    password = config.password
    maximumPoolSize = 10
}
val dataSource = HikariDataSource(hikariConfig)
```

### 3. Use Transações para Operações Múltiplas

```kotlin
// ✅ Atômico
transaction(connectionFactory) {
    insert<User>(...).execute()
    update<Order>(...).execute()
}

// ❌ Não atômico
insert<User>(...).execute()
update<Order>(...).execute()
```

### 4. Use Repository Pattern

Separe a lógica de acesso a dados da lógica de negócio:

```
[Controller] → [Service] → [Repository] → [Database]
```

## Exemplos Completos

Veja os arquivos de exemplo:
- `src/main/kotlin/com/aggitech/orm/examples/DeclarativeExamples.kt` - Exemplos completos da DSL
- `USAGE_GUIDE.md` - Documentação detalhada

## Bancos de Dados Suportados

- PostgreSQL (porta padrão: 5432)
- MySQL (porta padrão: 3306)

## Suporte a R2DBC (Programação Reativa)

O AggORM oferece suporte completo a **R2DBC** usando **Kotlin Coroutines**, proporcionando uma experiência de desenvolvimento que parece programação imperativa (async/await), mas é totalmente reativa por baixo.

### Instalação R2DBC

```kotlin
dependencies {
    implementation("com.github.Aggi-tech.AggORM:aggo-core:1.1.0")
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Driver R2DBC (escolha o seu banco)
    implementation("io.r2dbc:r2dbc-postgresql:1.0.5.RELEASE") // PostgreSQL
    // ou
    implementation("io.asyncer:r2dbc-mysql:1.0.5") // MySQL
}
```

### Configuração R2DBC

```kotlin
val config = R2dbcConfig(
    database = "myapp",
    host = "localhost",
    port = 5432,
    user = "postgres",
    password = "password",
    type = SupportedDatabases.POSTGRESQL
)

val connectionFactory = R2dbcConnectionFactory(config)
```

### Uso com Coroutines (Parece Síncrono!)

A API usa `suspend functions` que fazem o código parecer síncrono/imperativo:

```kotlin
suspend fun example() {
    val connection = connectionFactory.create() // suspend - aguarda conexão

    try {
        // SELECT - parece síncrono mas é reativo!
        val users = select<User> {
            where {
                User::age gte 18
            }
            orderBy {
                User::name.asc()
            }
        }.execute(connection, SqlDialect.POSTGRESQL) // suspend function

        users.forEach { user ->
            println("${user["name"]} - ${user["email"]}")
        }

        // INSERT
        val rowsInserted = insert<User> {
            User::name to "João"
            User::email to "joao@example.com"
            User::age to 30
        }.execute(connection, SqlDialect.POSTGRESQL)

        // UPDATE
        val rowsUpdated = update<User> {
            User::age to 31
            where {
                User::email eq "joao@example.com"
            }
        }.execute(connection, SqlDialect.POSTGRESQL)

        // DELETE
        val rowsDeleted = delete<User> {
            where {
                User::email eq "joao@example.com"
            }
        }.execute(connection, SqlDialect.POSTGRESQL)

    } finally {
        connection.close().awaitSingle()
    }
}
```

### Streaming com Flow

Para grandes volumes de dados, use `Flow` para processar sob demanda:

```kotlin
suspend fun streamExample() {
    val connection = connectionFactory.create()

    try {
        select<User> {
            where {
                User::age gte 18
            }
        }.executeAsFlow(connection, SqlDialect.POSTGRESQL) // Retorna Flow
            .collect { user -> // Processa um por vez
                processUser(user)
            }
    } finally {
        connection.close().awaitSingle()
    }
}
```

### Transações Reativas

Transações com sintaxe limpa e rollback automático:

```kotlin
suspend fun transactionExample() {
    val connection = connectionFactory.create()

    try {
        transaction(connection) {
            // Todas as operações são atômicas
            insert<User> { ... }.execute(connection, dialect)
            insert<Order> { ... }.execute(connection, dialect)

            // Commit automático ao sair do bloco
            // Rollback automático em caso de exceção
        }
    } finally {
        connection.close().awaitSingle()
    }
}
```

### Repository Pattern com R2DBC

```kotlin
class UserRepository(
    private val connectionFactory: R2dbcConnectionFactory,
    private val dialect: SqlDialect = SqlDialect.POSTGRESQL
) {

    suspend fun findAll(): List<Map<String, Any?>> {
        val connection = connectionFactory.create()
        return try {
            select<User> {
                orderBy { User::name.asc() }
            }.execute(connection, dialect)
        } finally {
            connection.close().awaitSingle()
        }
    }

    suspend fun create(name: String, email: String, age: Int): Long? {
        val connection = connectionFactory.create()
        return try {
            insert<User> {
                User::name to name
                User::email to email
                User::age to age
            }.executeReturningKeys(connection, dialect).firstOrNull()
        } finally {
            connection.close().awaitSingle()
        }
    }

    suspend fun streamAll(processor: suspend (Map<String, Any?>) -> Unit) {
        val connection = connectionFactory.create()
        try {
            select<User> {
                orderBy { User::id.asc() }
            }.executeAsFlow(connection, dialect)
                .collect { user -> processor(user) }
        } finally {
            connection.close().awaitSingle()
        }
    }
}
```

### Comparação: JDBC vs R2DBC

| Aspecto | JDBC (Tradicional) | R2DBC (Reativo) |
|---------|-------------------|-----------------|
| Bloqueio | Bloqueia thread | Não bloqueia |
| Concorrência | Thread pool | Event loop |
| Escalabilidade | Limitada por threads | Alta |
| API | Síncrona | Suspend functions (parece síncrona!) |
| Uso de memória | Uma thread por conexão | Compartilha threads |

### Por que R2DBC com Coroutines?

- **Performance**: Não bloqueia threads, ideal para alta concorrência
- **Simplicidade**: API com suspend functions parece código síncrono
- **Streaming**: Processa grandes resultados sem carregar tudo na memória
- **Padrão**: R2DBC é o padrão reativo para bancos relacionais
- **Sem frameworks**: Não depende de Spring ou outros frameworks

### Quando usar R2DBC?

- APIs de alta concorrência
- Microserviços reativos
- Processamento de streams de dados
- Aplicações com muitas conexões simultâneas

### Quando usar JDBC?

- Aplicações tradicionais
- Scripts simples
- Quando não precisa de alta concorrência
- Integração com frameworks legados

## Server-Sent Events (SSE) Streaming

O AggORM oferece suporte completo a **Server-Sent Events (SSE)** para streaming de dados em tempo real, mantendo a mesma API imperativa com suspend functions.

### Por que SSE?

- **Unidirecional**: Servidor → Cliente (perfeito para notificações, feeds, dashboards)
- **Mais leve que WebSockets**: HTTP simples, sem protocolo especial
- **Reconexão automática**: Navegadores reconectam automaticamente em caso de falha
- **Event IDs**: Suporte nativo a retomada após desconexão
- **Compatibilidade**: Funciona com proxies e load balancers HTTP padrão

### Instalação SSE

```kotlin
dependencies {
    implementation("com.github.Aggi-tech.AggORM:aggo-core:1.1.0")
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("io.r2dbc:r2dbc-postgresql:1.0.5.RELEASE")
}
```

### Uso Básico

```kotlin
suspend fun streamUsers() {
    val connection = connectionFactory.create()

    // Converte query em stream SSE - parece imperativo!
    select<User> {
        where { User::active eq true }
        orderBy { User::createdAt.desc() }
    }.toSSE(connection, SqlDialect.POSTGRESQL)
      .collect { event ->
          println("Event ${event.id}: ${event.data}")
      }
}
```

### Streaming com Heartbeat

Heartbeats mantêm a conexão viva e detectam desconexões:

```kotlin
select<Order> {
    where { Order::status eq "PENDING" }
}.toSSEWithHeartbeat(connection, SqlDialect.POSTGRESQL)
  .collect { event ->
      if (event.comment != null) {
          println("Heartbeat: ${event.comment}")
      } else {
          processOrder(event.data)
      }
  }
```

### Retomada após Desconexão

SSE suporta retomar streaming a partir do último evento recebido:

```kotlin
// Cliente envia Last-Event-ID header
val lastId = request.headers["Last-Event-ID"]

select<Notification> {
    if (lastId != null) {
        where { Notification::id gt lastId.toLong() }
    }
    orderBy { Notification::id.asc() }
}.toSSEFrom(connection, SqlDialect.POSTGRESQL, lastId)
  .collect { event ->
      // Salvar event.id para possível retomada
      saveLastEventId(event.id)
      processNotification(event.data)
  }
```

### Polling Periódico

Para monitoramento em tempo real:

```kotlin
select<Order> {
    where { Order::status eq "PENDING" }
    limit(10)
}.toSSEPolling(
    connection = connection,
    dialect = SqlDialect.POSTGRESQL,
    pollingInterval = 5000 // 5 segundos
).collect { event ->
    updateDashboard(event.data)
}
```

### Configuração SSE

```kotlin
val config = sseConfig {
    heartbeatInterval = Duration.ofSeconds(30)
    retryInterval = 5000
    bufferSize = 512
    enableHeartbeat = true
    eventIdGenerator = SseConfig.sequentialIdGenerator()
}

select<User> { ... }
    .toSSE(connection, dialect, config)
```

### Repository Pattern com SSE

```kotlin
class NotificationRepository(
    private val connectionFactory: R2dbcConnectionFactory,
    private val dialect: SqlDialect
) {
    suspend fun streamNotifications(
        userId: Long,
        processor: suspend (String) -> Unit
    ) {
        val connection = connectionFactory.create()

        try {
            select<Notification> {
                where {
                    (Notification::userId eq userId) and
                    (Notification::read eq false)
                }
            }.toSSEWithHeartbeat(connection, dialect)
              .collect { event ->
                  if (event.data.isNotEmpty()) {
                      processor(event.data)
                  }
              }
        } finally {
            connection.close().awaitSingle()
        }
    }
}
```

### Connection Pool para SSE

Para alta concorrência, use connection pooling:

```kotlin
val pool = R2dbcConnectionPool(
    config = config,
    poolConfig = PoolConfig.highConcurrency()
)

pool.initialize()

try {
    pool.withConnection { connection ->
        select<User> { ... }
            .toSSEWithHeartbeat(connection, dialect)
            .collect { event -> processEvent(event) }
    }

    // Estatísticas
    val stats = pool.getStats()
    println("Pool: ${stats.activeConnections}/${stats.maxSize}")
} finally {
    pool.close()
}
```

### Cliente JavaScript

```javascript
const eventSource = new EventSource('/api/notifications/stream');

eventSource.addEventListener('message', (event) => {
    console.log('Data:', JSON.parse(event.data));
    console.log('ID:', event.lastEventId);

    // Salvar para retomada
    localStorage.setItem('lastEventId', event.lastEventId);
});

eventSource.addEventListener('error', () => {
    console.log('Reconectando...');
    // Reconexão automática com Last-Event-ID
});
```

### SSE vs WebSockets

| Característica | SSE | WebSockets |
|---------------|-----|------------|
| Direção | Unidirecional (Servidor → Cliente) | Bidirecional |
| Protocolo | HTTP simples | WS/WSS protocolo especial |
| Reconexão | Automática pelo navegador | Manual |
| Retomada | Event IDs nativos | Implementação custom |
| Complexidade | Simples | Mais complexo |
| Proxies/CDN | Compatível | Pode ter problemas |
| Uso ideal | Notificações, feeds, dashboards | Chat, games, colaboração |

### Funcionalidades SSE

- ✅ **Streaming básico**: Converte queries em eventos SSE
- ✅ **Heartbeat automático**: Keep-alive configurável
- ✅ **Retry automático**: Campo `retry:` para reconexão
- ✅ **Event IDs**: Retomada após desconexão
- ✅ **Tipos de eventos**: Filtragem por tipo no cliente
- ✅ **Polling periódico**: Monitoramento em tempo real
- ✅ **Connection pooling**: Alta performance
- ✅ **Framework agnostic**: Core independente de frameworks

### Formato SSE

O formato SSE segue a especificação RFC 6455:

```
id: 123
event: notification
retry: 3000
data: {"user":"John","message":"Hello"}

: heartbeat

id: 124
data: {"user":"Jane","message":"Hi"}

```

## Novas APIs v1.1.0

### Connection Manager (JDBC)

```kotlin
// Registra configuração uma vez
JdbcConnectionManager.register(config = dbConfig)

// Execute queries em qualquer lugar
val users = select<User> { ... }.execute()
val user = select<User> { ... }.executeAsEntityOrNull()
val userEntities = select<User> { ... }.executeAsEntities()
```

### Connection Manager (R2DBC)

```kotlin
// Registra configuração uma vez
R2dbcConnectionManager.register(config = r2dbcConfig)

// Execute queries reativas
val users = select<User> { ... }.execute() // suspend
val user = select<User> { ... }.executeAsEntityOrNull() // suspend

// Streaming com Flow
select<User> { ... }.executeAsEntityFlow()
    .collect { user -> ... }
```

### Mapeamento Automático

```kotlin
// Antes (v1.0.x)
val results: List<Map<String, Any?>> = select<User> { ... }.execute()
val users = results.map {
    User(
        id = it["id"] as Long,
        name = it["name"] as String,
        // ...
    )
}

// Agora (v1.1.0)
val users: List<User> = select<User> { ... }.executeAsEntities()
```

### Logging de Queries

```kotlin
// Habilita logging
QueryLoggerManager.enableConsoleLogging(
    logLevel = LogLevel.DEBUG,
    includeParameters = true,
    includeExecutionTime = true
)

// Output:
// [AggORM SELECT] SELECT "name", "email", "age" FROM "user" WHERE "age" >= ?
//   Parameters: 18
// [AggORM SELECT] Completed in 15ms - 42 row(s) affected
```

### Transações

```kotlin
// JDBC
transaction {
    insert<User> { ... }.execute()
    insert<Order> { ... }.execute()
    // Commit automático
}

// R2DBC
suspend fun example() {
    transaction {
        insert<User> { ... }.execute()
        insert<Order> { ... }.execute()
        // Commit automático
    }
}
```

### Exemplos Completos

Veja exemplos completos em:
- `aggo-core/src/main/kotlin/com/aggitech/orm/examples/JdbcExample.kt`
- `aggo-core/src/main/kotlin/com/aggitech/orm/examples/R2dbcExample.kt`

## Roadmap

- [x] Suporte a R2DBC para operações reativas
- [x] Suporte a Server-Sent Events (SSE)
- [x] Connection pooling integrado
- [x] Connection Manager com pool automático
- [x] Mapeamento automático para entidades
- [x] Sistema de logging de queries
- [x] R2DBC thread-safe com Coroutines
- [ ] Adaptadores para Spring WebFlux e Ktor
- [ ] Suporte a migrations
- [ ] Geração automática de schema
- [ ] Cache de queries
- [ ] Suporte a mais bancos (SQLite, SQL Server, Oracle)

## Licença

MIT License

## Contribuindo

Contribuições são bem-vindas! Sinta-se à vontade para abrir issues ou pull requests.
