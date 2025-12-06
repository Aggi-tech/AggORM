# Spring Boot - Configuração

O módulo `aggo-spring-boot-starter` fornece auto-configuração completa para integração do AggORM com Spring Boot.

## Instalação

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.aggitech.orm:aggo-spring-boot-starter:1.4.0")

    // Driver do banco de dados
    runtimeOnly("org.postgresql:postgresql:42.7.1")
}
```

### Maven

```xml
<dependency>
    <groupId>com.aggitech.orm</groupId>
    <artifactId>aggo-spring-boot-starter</artifactId>
    <version>1.4.0</version>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.1</version>
    <scope>runtime</scope>
</dependency>
```

## Configuração via YAML

### application.yml

```yaml
aggo:
  orm:
    # Configuração do banco de dados
    database: myapp
    host: localhost
    port: 5432
    username: postgres
    password: ${DB_PASSWORD}
    database-type: POSTGRESQL

    # Configuração de migrations (opcional)
    migrations:
      enabled: true
      base-package: com.example.migrations
      show-details: true
      fail-on-error: true
      validate-checksums: true
```

### Propriedades Disponíveis

#### Banco de Dados

| Propriedade | Tipo | Default | Descrição |
|-------------|------|---------|-----------|
| `aggo.orm.database` | String | - | Nome do banco de dados |
| `aggo.orm.host` | String | `localhost` | Host do servidor |
| `aggo.orm.port` | Integer | `5432` | Porta do servidor |
| `aggo.orm.username` | String | - | Usuário de conexão |
| `aggo.orm.password` | String | - | Senha de conexão |
| `aggo.orm.database-type` | String | `POSTGRESQL` | Tipo do banco |

#### Migrations

| Propriedade | Tipo | Default | Descrição |
|-------------|------|---------|-----------|
| `aggo.orm.migrations.enabled` | Boolean | `true` | Habilita migrations automáticas |
| `aggo.orm.migrations.base-package` | String | - | Pacote para scan de migrations |
| `aggo.orm.migrations.show-details` | Boolean | `true` | Exibe detalhes no log |
| `aggo.orm.migrations.fail-on-error` | Boolean | `true` | Para aplicação se falhar |
| `aggo.orm.migrations.validate-checksums` | Boolean | `true` | Valida checksums |

## Configuração via Properties

### application.properties

```properties
# Banco de dados
aggo.orm.database=myapp
aggo.orm.host=localhost
aggo.orm.port=5432
aggo.orm.username=postgres
aggo.orm.password=${DB_PASSWORD}
aggo.orm.database-type=POSTGRESQL

# Migrations
aggo.orm.migrations.enabled=true
aggo.orm.migrations.base-package=com.example.migrations
aggo.orm.migrations.show-details=true
aggo.orm.migrations.fail-on-error=true
aggo.orm.migrations.validate-checksums=true
```

## Profiles

### Desenvolvimento

```yaml
# application-dev.yml
aggo:
  orm:
    database: myapp_dev
    host: localhost
    username: dev_user
    password: dev_password
    migrations:
      show-details: true
```

### Produção

```yaml
# application-prod.yml
aggo:
  orm:
    database: ${DB_NAME}
    host: ${DB_HOST}
    port: ${DB_PORT:5432}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    migrations:
      show-details: false
      fail-on-error: true
```

### Testes

```yaml
# application-test.yml
aggo:
  orm:
    database: myapp_test
    host: localhost
    username: test_user
    password: test_password
    migrations:
      enabled: true
```

## Auto-Configuration

### Beans Criados Automaticamente

O starter cria os seguintes beans:

#### DbConfig

```kotlin
@Bean
fun aggoDbConfig(): DbConfig {
    return DbConfig(
        database = properties.database,
        host = properties.host,
        port = properties.port,
        user = properties.username,
        password = properties.password,
        type = properties.databaseType
    )
}
```

#### SqlDialect

```kotlin
@Bean
fun aggoSqlDialect(): SqlDialect {
    return properties.databaseType.dialect
}
```

#### TransactionManager

```kotlin
@Bean
fun aggoTransactionManager(): AggoTransactionManager {
    return AggoTransactionManager(dbConfig)
}
```

### Condições de Ativação

A auto-configuração é ativada quando:

1. O starter está no classpath
2. Propriedades obrigatórias estão configuradas
3. Não existe bean customizado já definido

## Configuração Manual

### Sobrescrevendo Beans

Você pode fornecer beans customizados:

```kotlin
@Configuration
class CustomAggoConfig {

    @Bean
    @Primary
    fun customDbConfig(): DbConfig {
        return DbConfig(
            database = "custom_db",
            user = "custom_user",
            password = getPasswordFromVault()
        )
    }

    private fun getPasswordFromVault(): String {
        // Buscar senha de um vault
        return vaultClient.getSecret("db-password")
    }
}
```

### Desabilitando Auto-Configuration

```kotlin
@SpringBootApplication(exclude = [
    AggoOrmAutoConfiguration::class
])
class MyApplication
```

Ou via properties:

```yaml
spring:
  autoconfigure:
    exclude:
      - com.aggitech.orm.spring.autoconfigure.AggoOrmAutoConfiguration
```

## Integração com DataSource

### Usando DataSource Existente

Se você já tem um `DataSource` configurado (ex: HikariCP), o starter pode usá-lo:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp
    username: postgres
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

aggo:
  orm:
    # Não precisa repetir configurações se usar o DataSource do Spring
```

## Múltiplos Bancos de Dados

### Configuração

```yaml
aggo:
  orm:
    # Banco primário
    database: primary_db
    host: primary-host
    username: primary_user
    password: ${PRIMARY_DB_PASSWORD}

# Bancos adicionais via código
```

### Configuração Manual para Múltiplos Bancos

```kotlin
@Configuration
class MultiDatabaseConfig {

    @Bean
    @Primary
    fun primaryDbConfig(): DbConfig {
        return DbConfig(
            database = "primary",
            host = "primary-host",
            user = "user",
            password = "password"
        )
    }

    @Bean("analyticsDbConfig")
    fun analyticsDbConfig(): DbConfig {
        return DbConfig(
            database = "analytics",
            host = "analytics-host",
            user = "analytics_user",
            password = "password"
        )
    }

    @PostConstruct
    fun registerConnections() {
        JdbcConnectionManager.register(primaryDbConfig(), "primary")
        JdbcConnectionManager.register(analyticsDbConfig(), "analytics")
    }
}
```

## Logging

### Configuração de Logs

```yaml
logging:
  level:
    com.aggitech.orm: DEBUG
    com.aggitech.orm.spring: INFO
```

### Logs de Inicialização

```
INFO  AggoOrmAutoConfiguration : Configuring AggORM with database: myapp@localhost:5432
INFO  AggoMigrationsAutoConfiguration : AggORM Migrations: Starting migration process...
INFO  AggoMigrationsAutoConfiguration : AggORM Migrations: Found 5 migration(s)
INFO  AggoMigrationsAutoConfiguration : AggORM Migrations: [OK] V001 - CreateUsersTable (45ms)
INFO  AggoMigrationsAutoConfiguration : AggORM Migrations: Completed successfully. Executed: 5, Skipped: 0, Failed: 0
```

## Health Checks

### Atuator Endpoint (se disponível)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  health:
    db:
      enabled: true
```

## Exemplo Completo

### Aplicação Spring Boot

```kotlin
@SpringBootApplication
class MyApplication

fun main(args: Array<String>) {
    runApplication<MyApplication>(*args)
}
```

### application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: my-app

aggo:
  orm:
    database: myapp
    host: ${DB_HOST:localhost}
    port: ${DB_PORT:5432}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD}
    database-type: POSTGRESQL
    migrations:
      enabled: true
      base-package: com.example.migrations
      show-details: true
      fail-on-error: true

logging:
  level:
    com.aggitech.orm: INFO
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_HOST: db
      DB_PORT: 5432
      DB_USER: postgres
      DB_PASSWORD: password
    depends_on:
      - db

  db:
    image: postgres:15
    environment:
      POSTGRES_DB: myapp
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

## Troubleshooting

### Bean não encontrado

```
No qualifying bean of type 'DbConfig' available
```

**Solução**: Verifique se as propriedades obrigatórias estão configuradas.

### Erro de conexão

```
Connection refused to host: localhost:5432
```

**Solução**: Verifique se o banco de dados está rodando e acessível.

### Migration falhou

```
AggORM Migrations: [FAIL] V001 - CreateUsersTable
```

**Solução**: Verifique os logs de erro e a migration correspondente.

## Próximos Passos

- [Repositories](./02-repositories.md) - Padrão Repository
- [Transactions](./03-transactions.md) - Gerenciamento de transações
- [Migrations](./04-migrations.md) - Migrations automáticas
