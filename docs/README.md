# AggORM - Type-Safe Kotlin ORM

AggORM é um framework ORM type-safe para Kotlin que oferece uma DSL intuitiva para operações de banco de dados, suporte a programação reativa com R2DBC, sistema de migrations e integração com Spring Boot.

## Sumário

- [Instalação](#instalação)
- [Quick Start](#quick-start)
- [Módulos](#módulos)
- [Documentação](#documentação)

## Instalação

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // Core - JDBC e R2DBC
    implementation("com.aggitech.orm:aggo-core:1.4.0")

    // Migrations (opcional)
    implementation("com.aggitech.orm:aggo-migrations:1.4.0")

    // Spring Boot Starter (opcional)
    implementation("com.aggitech.orm:aggo-spring-boot-starter:1.4.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    // Core - JDBC e R2DBC
    implementation 'com.aggitech.orm:aggo-core:1.4.0'

    // Migrations (opcional)
    implementation 'com.aggitech.orm:aggo-migrations:1.4.0'

    // Spring Boot Starter (opcional)
    implementation 'com.aggitech.orm:aggo-spring-boot-starter:1.4.0'
}
```

### Maven

```xml
<dependencies>
    <!-- Core - JDBC e R2DBC -->
    <dependency>
        <groupId>com.aggitech.orm</groupId>
        <artifactId>aggo-core</artifactId>
        <version>1.4.0</version>
    </dependency>

    <!-- Migrations (opcional) -->
    <dependency>
        <groupId>com.aggitech.orm</groupId>
        <artifactId>aggo-migrations</artifactId>
        <version>1.4.0</version>
    </dependency>

    <!-- Spring Boot Starter (opcional) -->
    <dependency>
        <groupId>com.aggitech.orm</groupId>
        <artifactId>aggo-spring-boot-starter</artifactId>
        <version>1.4.0</version>
    </dependency>
</dependencies>
```

## Quick Start

### 1. Configurar Conexão

```kotlin
import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.enums.SupportedDatabases
import com.aggitech.orm.jdbc.JdbcConnectionManager

val config = DbConfig(
    database = "myapp",
    host = "localhost",
    port = 5432,
    user = "postgres",
    password = "password",
    type = SupportedDatabases.POSTGRESQL
)

JdbcConnectionManager.register(config)
```

### 2. Definir Entidade

```kotlin
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val age: Int,
    val active: Boolean = true
)
```

### 3. Executar Queries

```kotlin
import com.aggitech.orm.dsl.*
import com.aggitech.orm.jdbc.execute

// SELECT
val users = select<User> {
    where { User::active eq true }
    orderBy(User::name, OrderDirection.ASC)
    limit(10)
}.execute()

// INSERT
val user = User(0, "John Doe", "john@example.com", 30)
insert(user).execute()

// UPDATE
update<User> {
    set(User::name, "John Updated")
    where { User::id eq 1L }
}.execute()

// DELETE
delete<User> {
    where { User::id eq 1L }
}.execute()
```

## Módulos

### aggo-core

Módulo principal contendo:
- **Query DSL**: Construção type-safe de queries SQL
- **JDBC**: Execução síncrona de queries
- **R2DBC**: Execução reativa com Kotlin Coroutines
- **SSE**: Server-Sent Events para streaming de dados
- **Entity Mapping**: Mapeamento automático de resultados
- **Validation**: Validação de entidades com annotations
- **Logging**: Sistema plugável de logs

### aggo-migrations

Sistema de migrations de banco de dados:
- **Type-Safe DSL**: Criação de tabelas com referências de propriedades
- **Versionamento**: Controle automático de versões
- **Rollback**: Suporte a reversão de migrations
- **Checksum**: Validação de integridade

### aggo-spring-boot-starter

Integração com Spring Boot:
- **Auto-configuration**: Configuração automática via `application.yml`
- **Repository Pattern**: Implementação de `CrudRepository`
- **Transaction Management**: Suporte a `@Transactional`
- **Migrations Automáticas**: Execução na inicialização

## Documentação

### Configuração
- [Guia de Configuração](./configuration.md) - Configuração completa de todos os módulos

### JDBC
- [Query DSL](./jdbc/01-query-dsl.md) - Construção type-safe de queries
- [Gerenciamento de Conexões](./jdbc/02-connection-management.md) - Pool de conexões JDBC
- [Mapeamento de Entidades](./jdbc/03-entity-mapping.md) - Conversão automática de resultados
- [Validação](./jdbc/04-validation.md) - Validação com annotations
- [Logging](./jdbc/05-logging.md) - Sistema de logs de queries

### R2DBC
- [Queries Reativas](./r2dbc/01-reactive-queries.md) - Operações assíncronas com Coroutines
- [Gerenciamento de Conexões](./r2dbc/02-connection-management.md) - Pool de conexões R2DBC
- [Server-Sent Events](./r2dbc/03-sse.md) - Streaming de dados em tempo real

### Migrations
- [Introdução](./migrations/01-introduction.md) - Conceitos básicos
- [Criando Migrations](./migrations/02-creating-migrations.md) - DSL para criação de tabelas
- [Executando Migrations](./migrations/03-running-migrations.md) - Execução e rollback

### Spring Boot
- [Configuração](./springboot/01-configuration.md) - Auto-configuration
- [Repositories](./springboot/02-repositories.md) - Padrão Repository
- [Transactions](./springboot/03-transactions.md) - Gerenciamento de transações
- [Migrations](./springboot/04-migrations.md) - Migrations automáticas

## Bancos de Dados Suportados

| Banco | JDBC | R2DBC | Migrations |
|-------|------|-------|------------|
| PostgreSQL | Sim | Sim | Sim |
| MySQL | Sim | Sim | Sim |

## Requisitos

- **JDK**: 21+
- **Kotlin**: 2.0+
- **Spring Boot**: 3.2+ (para o starter)

## Licença

MIT License - veja [LICENSE](../LICENSE) para detalhes.
