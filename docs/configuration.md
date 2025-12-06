# Guia de Configuração

Este guia cobre todas as opções de configuração do AggORM para os diferentes módulos e cenários de uso.

## Sumário

- [Configuração JDBC](#configuração-jdbc)
- [Configuração R2DBC](#configuração-r2dbc)
- [Configuração Spring Boot](#configuração-spring-boot)
- [Configuração de Migrations](#configuração-de-migrations)
- [Múltiplos Bancos de Dados](#múltiplos-bancos-de-dados)

## Configuração JDBC

### DbConfig

A classe `DbConfig` é a configuração principal para conexões JDBC:

```kotlin
import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.enums.SupportedDatabases

val config = DbConfig(
    database = "myapp",           // Nome do banco de dados
    host = "localhost",           // Host do servidor (default: localhost)
    port = 5432,                  // Porta (default: 5432 para PostgreSQL)
    user = "postgres",            // Usuário
    password = "password",        // Senha
    type = SupportedDatabases.POSTGRESQL  // Tipo do banco
)
```

### Propriedades Disponíveis

| Propriedade | Tipo | Default | Descrição |
|-------------|------|---------|-----------|
| `database` | String | - | Nome do banco de dados (obrigatório) |
| `host` | String | `localhost` | Host do servidor |
| `port` | Int | `5432` | Porta do servidor |
| `user` | String | - | Usuário de conexão (obrigatório) |
| `password` | String | - | Senha de conexão (obrigatório) |
| `type` | SupportedDatabases | `POSTGRESQL` | Tipo do banco de dados |

### Registrando a Configuração

```kotlin
import com.aggitech.orm.jdbc.JdbcConnectionManager

// Configuração padrão (sem nome)
JdbcConnectionManager.register(config)

// Configuração nomeada
JdbcConnectionManager.register(config, "primary")
```

### Bancos de Dados Suportados

```kotlin
import com.aggitech.orm.enums.SupportedDatabases

// PostgreSQL (default)
val postgresConfig = DbConfig(
    database = "mydb",
    host = "localhost",
    port = 5432,
    user = "postgres",
    password = "password",
    type = SupportedDatabases.POSTGRESQL
)

// MySQL
val mysqlConfig = DbConfig(
    database = "mydb",
    host = "localhost",
    port = 3306,
    user = "root",
    password = "password",
    type = SupportedDatabases.MYSQL
)
```

### URL JDBC Gerada

A URL JDBC é gerada automaticamente com base no tipo do banco:

```kotlin
val config = DbConfig(
    database = "mydb",
    host = "localhost",
    port = 5432,
    user = "postgres",
    password = "password",
    type = SupportedDatabases.POSTGRESQL
)

println(config.url)
// Output: jdbc:postgresql://localhost:5432/mydb
```

## Configuração R2DBC

### R2dbcConfig

Para conexões reativas, use `R2dbcConfig`:

```kotlin
import com.aggitech.orm.config.R2dbcConfig
import com.aggitech.orm.enums.SupportedDatabases

val r2dbcConfig = R2dbcConfig(
    database = "myapp",
    host = "localhost",
    port = 5432,
    user = "postgres",
    password = "password",
    type = SupportedDatabases.POSTGRESQL
)
```

### Registrando Configuração R2DBC

```kotlin
import com.aggitech.orm.r2dbc.R2dbcConnectionManager

// Configuração padrão
R2dbcConnectionManager.register(r2dbcConfig)

// Configuração nomeada
R2dbcConnectionManager.register(r2dbcConfig, "reactive")
```

## Configuração Spring Boot

### application.yml

Configure o AggORM via `application.yml`:

```yaml
aggo:
  orm:
    # Configuração do banco de dados
    database: myapp
    host: localhost
    port: 5432
    username: postgres
    password: password
    database-type: POSTGRESQL

    # Configuração de migrations
    migrations:
      enabled: true
      base-package: com.example.migrations
      show-details: true
      fail-on-error: true
      validate-checksums: true
```

### application.properties

Alternativa em formato properties:

```properties
aggo.orm.database=myapp
aggo.orm.host=localhost
aggo.orm.port=5432
aggo.orm.username=postgres
aggo.orm.password=password
aggo.orm.database-type=POSTGRESQL

aggo.orm.migrations.enabled=true
aggo.orm.migrations.base-package=com.example.migrations
aggo.orm.migrations.show-details=true
aggo.orm.migrations.fail-on-error=true
aggo.orm.migrations.validate-checksums=true
```

### Propriedades do Spring Boot

| Propriedade | Tipo | Default | Descrição |
|-------------|------|---------|-----------|
| `aggo.orm.database` | String | - | Nome do banco de dados |
| `aggo.orm.host` | String | `localhost` | Host do servidor |
| `aggo.orm.port` | Integer | `5432` | Porta do servidor |
| `aggo.orm.username` | String | - | Usuário de conexão |
| `aggo.orm.password` | String | - | Senha de conexão |
| `aggo.orm.database-type` | String | `POSTGRESQL` | Tipo do banco |

### Propriedades de Migrations

| Propriedade | Tipo | Default | Descrição |
|-------------|------|---------|-----------|
| `aggo.orm.migrations.enabled` | Boolean | `true` | Habilita migrations automáticas |
| `aggo.orm.migrations.base-package` | String | - | Pacote base para scan |
| `aggo.orm.migrations.show-details` | Boolean | `true` | Exibe detalhes no log |
| `aggo.orm.migrations.fail-on-error` | Boolean | `true` | Para aplicação se falhar |
| `aggo.orm.migrations.validate-checksums` | Boolean | `true` | Valida checksums |

## Configuração de Migrations

### Executor Manual

Para executar migrations manualmente (sem Spring Boot):

```kotlin
import com.aggitech.orm.migrations.executor.JdbcMigrationExecutor
import com.aggitech.orm.migrations.executor.MigrationScanner
import com.aggitech.orm.migrations.history.JdbcMigrationHistoryRepository
import java.sql.DriverManager

// Criar conexão
val connection = DriverManager.getConnection(
    config.url,
    config.user,
    config.password
)

// Criar executor
val historyRepository = JdbcMigrationHistoryRepository(connection, config.dialect)
val executor = JdbcMigrationExecutor(connection, config.dialect, historyRepository)
val scanner = MigrationScanner()

// Carregar e executar migrations
val migrations = scanner.loadMigrations(listOf(
    V001_CreateUsersTable::class,
    V002_CreatePostsTable::class
))

val result = executor.migrate(migrations)

println("Executadas: ${result.totalExecuted}")
println("Ignoradas: ${result.totalSkipped}")
println("Falhas: ${result.totalFailed}")
```

## Múltiplos Bancos de Dados

### Configurando Múltiplas Conexões

```kotlin
// Banco principal
val primaryConfig = DbConfig(
    database = "primary",
    host = "primary-db.example.com",
    port = 5432,
    user = "app_user",
    password = "password",
    type = SupportedDatabases.POSTGRESQL
)

// Banco de analytics
val analyticsConfig = DbConfig(
    database = "analytics",
    host = "analytics-db.example.com",
    port = 5432,
    user = "analytics_user",
    password = "password",
    type = SupportedDatabases.POSTGRESQL
)

// Banco de cache (MySQL)
val cacheConfig = DbConfig(
    database = "cache",
    host = "cache-db.example.com",
    port = 3306,
    user = "cache_user",
    password = "password",
    type = SupportedDatabases.MYSQL
)

// Registrar com nomes distintos
JdbcConnectionManager.register(primaryConfig, "primary")
JdbcConnectionManager.register(analyticsConfig, "analytics")
JdbcConnectionManager.register(cacheConfig, "cache")
```

### Usando Configurações Nomeadas

```kotlin
// Query no banco primário (padrão)
val users = select<User> {
    where { User::active eq true }
}.execute()

// Query no banco de analytics
val events = select<AnalyticsEvent> {
    where { AnalyticsEvent::timestamp gte today }
}.execute(configName = "analytics")

// Query no banco de cache
val cachedData = select<CacheEntry> {
    where { CacheEntry::key eq "user:123" }
}.execute(configName = "cache")
```

## Configuração de Logging

### Logger Padrão

```kotlin
import com.aggitech.orm.logging.ConsoleQueryLogger
import com.aggitech.orm.logging.LogLevel

// Criar logger
val logger = ConsoleQueryLogger(
    minLevel = LogLevel.DEBUG,
    showParameters = true
)

// Usar com configuração (se suportado)
// O logger pode ser configurado globalmente
```

### Níveis de Log

| Nível | Descrição |
|-------|-----------|
| `TRACE` | Logs muito detalhados |
| `DEBUG` | Informações de debug (padrão) |
| `INFO` | Informações gerais |
| `WARN` | Avisos |
| `ERROR` | Erros |
| `OFF` | Desabilita logging |

## Configuração por Ambiente

### Desenvolvimento

```kotlin
val devConfig = DbConfig(
    database = "myapp_dev",
    host = "localhost",
    port = 5432,
    user = "dev_user",
    password = "dev_password",
    type = SupportedDatabases.POSTGRESQL
)
```

### Produção

```kotlin
val prodConfig = DbConfig(
    database = System.getenv("DB_NAME"),
    host = System.getenv("DB_HOST"),
    port = System.getenv("DB_PORT").toInt(),
    user = System.getenv("DB_USER"),
    password = System.getenv("DB_PASSWORD"),
    type = SupportedDatabases.POSTGRESQL
)
```

### Spring Boot Profiles

```yaml
# application-dev.yml
aggo:
  orm:
    database: myapp_dev
    host: localhost
    username: dev_user
    password: dev_password

---
# application-prod.yml
aggo:
  orm:
    database: ${DB_NAME}
    host: ${DB_HOST}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Usar variáveis de ambiente em produção
val config = DbConfig(
    database = System.getenv("DB_NAME"),
    user = System.getenv("DB_USER"),
    password = System.getenv("DB_PASSWORD")
)

// Registrar uma vez na inicialização
fun main() {
    JdbcConnectionManager.register(config)
    // ... resto da aplicação
}

// Usar nomes descritivos para múltiplos bancos
JdbcConnectionManager.register(config, "users-db")
JdbcConnectionManager.register(analyticsConfig, "analytics-db")
```

### [AVOID] Evitar

```kotlin
// Não hardcode senhas no código
val config = DbConfig(
    password = "minha-senha-123"  // [AVOID] Hardcoded
)

// Não registrar múltiplas vezes
repeat(100) {
    JdbcConnectionManager.register(config)  // [AVOID] Registro repetido
}

// Não esquecer de fechar conexões ao encerrar
// [AVOID] Sem cleanup
```

## Troubleshooting

### Erro de Conexão

```
Connection refused to host: localhost:5432
```

**Solução**: Verifique se o banco de dados está rodando e acessível.

### Erro de Autenticação

```
FATAL: password authentication failed for user "postgres"
```

**Solução**: Verifique usuário e senha no `DbConfig`.

### Driver não Encontrado

```
No suitable driver found for jdbc:postgresql://localhost:5432/mydb
```

**Solução**: Adicione o driver JDBC ao classpath:

```kotlin
dependencies {
    runtimeOnly("org.postgresql:postgresql:42.7.1")
}
```
