# Spring Boot - Migrations

O AggORM integra migrations com o ciclo de vida do Spring Boot, executando automaticamente na inicialização.

## Configuração

### application.yml

```yaml
aggo:
  orm:
    database: myapp
    host: localhost
    username: postgres
    password: ${DB_PASSWORD}

    migrations:
      enabled: true
      base-package: com.example.migrations
      show-details: true
      fail-on-error: true
      validate-checksums: true
```

### Propriedades

| Propriedade | Tipo | Default | Descrição |
|-------------|------|---------|-----------|
| `enabled` | Boolean | `true` | Habilita execução automática |
| `base-package` | String | - | Pacote para scan de migrations |
| `show-details` | Boolean | `true` | Mostra detalhes nos logs |
| `fail-on-error` | Boolean | `true` | Para aplicação se migration falhar |
| `validate-checksums` | Boolean | `true` | Valida checksums de migrations |

## Criando Migrations

### Estrutura de Diretório

```
src/main/kotlin/
└── com/example/
    ├── Application.kt
    └── migrations/
        ├── V001_CreateUsersTable.kt
        ├── V002_CreatePostsTable.kt
        ├── V003_AddIndexToUsers.kt
        └── V004_CreateCommentsTable.kt
```

### Exemplo de Migration

```kotlin
package com.example.migrations

import com.aggitech.orm.migrations.core.Migration

class V001_CreateUsersTable : Migration() {

    override fun up() {
        createTable<User> {
            column(User::id).bigInteger().primaryKey().autoIncrement()
            column(User::name).varchar(100).notNull()
            column(User::email).varchar(255).notNull().unique()
            column(User::createdAt).timestamp().notNull().default("CURRENT_TIMESTAMP")
        }
    }

    override fun down() {
        dropTable<User>()
    }
}
```

## Registro de Migrations

### Opção 1: Registro Manual (Recomendado)

```kotlin
package com.example.config

import com.aggitech.orm.spring.autoconfigure.MigrationRegistry
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct

@Configuration
class MigrationConfig(
    private val migrationRegistry: MigrationRegistry
) {

    @PostConstruct
    fun registerMigrations() {
        migrationRegistry.register(V001_CreateUsersTable::class)
        migrationRegistry.register(V002_CreatePostsTable::class)
        migrationRegistry.register(V003_AddIndexToUsers::class)
        migrationRegistry.register(V004_CreateCommentsTable::class)
    }
}
```

### Opção 2: Usando @EnableAggoMigrations

```kotlin
package com.example.config

import com.aggitech.orm.spring.migrations.EnableAggoMigrations
import com.aggitech.orm.spring.migrations.MigrationConfiguration
import org.springframework.context.annotation.Configuration

@Configuration
@EnableAggoMigrations(basePackages = ["com.example.migrations"])
class MigrationConfig : MigrationConfiguration()
```

## Fluxo de Execução

### Ordem de Execução

1. **Inicialização do Spring**: Contexto carrega
2. **Auto-Configuration**: Beans do AggORM são criados
3. **MigrationRunner**: Executado via `@PostConstruct`
4. **Validação**: Checksums verificados (se habilitado)
5. **Execução**: Migrations pendentes são aplicadas
6. **Logging**: Resultados são logados
7. **Erro**: Aplicação para (se `fail-on-error = true`)

### Logs de Execução

```
INFO  AggoMigrationsAutoConfiguration : AggORM Migrations: Starting migration process...
INFO  AggoMigrationsAutoConfiguration : AggORM Migrations: Found 4 migration(s)
INFO  AggoMigrationsAutoConfiguration : AggORM Migrations: [OK] V001 - CreateUsersTable (45ms)
INFO  AggoMigrationsAutoConfiguration : AggORM Migrations: [OK] V002 - CreatePostsTable (32ms)
DEBUG AggoMigrationsAutoConfiguration : AggORM Migrations: [SKIP] V003 - AddIndexToUsers (already applied)
INFO  AggoMigrationsAutoConfiguration : AggORM Migrations: [OK] V004 - CreateCommentsTable (28ms)
INFO  AggoMigrationsAutoConfiguration : AggORM Migrations: Completed successfully. Executed: 3, Skipped: 1, Failed: 0
```

## Controle de Execução

### Desabilitar em Ambiente Específico

```yaml
# application-test.yml
aggo:
  orm:
    migrations:
      enabled: false  # Desabilita em testes
```

### Desabilitar via Property

```bash
java -jar app.jar --aggo.orm.migrations.enabled=false
```

### Desabilitar Programaticamente

```kotlin
@Configuration
@ConditionalOnProperty(
    prefix = "app",
    name = ["run-migrations"],
    havingValue = "true",
    matchIfMissing = true
)
class MigrationConfig(
    private val migrationRegistry: MigrationRegistry
) {
    // ...
}
```

## Validação

### Checksum Mismatch

Se uma migration foi modificada após aplicação:

```
ERROR AggoMigrationsAutoConfiguration : AggORM Migrations: Migration validation failed:
  - V001_CreateUsersTable: Checksum mismatch (expected: abc123, actual: def456)

Application startup failed
```

### Desabilitar Validação (Não Recomendado)

```yaml
aggo:
  orm:
    migrations:
      validate-checksums: false  # [AVOID] Pode causar inconsistências
```

## Rollback

### Rollback Manual

O Spring Boot starter não executa rollbacks automaticamente. Para rollback, use CLI ou código manual:

```kotlin
@Component
class MigrationCommands(
    private val migrationExecutor: MigrationExecutor
) {

    fun rollback(steps: Int = 1) {
        val result = migrationExecutor.rollback(steps)

        result.executed.forEach {
            println("[OK] Rolled back: V${it.version} - ${it.description}")
        }
    }
}
```

### Rollback via Endpoint (Opcional)

```kotlin
@RestController
@RequestMapping("/admin/migrations")
@ConditionalOnProperty("app.admin.enabled", havingValue = "true")
class MigrationAdminController(
    private val migrationExecutor: MigrationExecutor
) {

    @PostMapping("/rollback")
    fun rollback(@RequestParam steps: Int = 1): Map<String, Any> {
        val result = migrationExecutor.rollback(steps)

        return mapOf(
            "success" to result.success,
            "rolledBack" to result.executed.map { it.version }
        )
    }
}
```

## Migrations por Ambiente

### Desenvolvimento

```kotlin
@Configuration
@Profile("dev")
class DevMigrationConfig(
    private val migrationRegistry: MigrationRegistry
) {

    @PostConstruct
    fun registerMigrations() {
        // Migrations de produção
        migrationRegistry.register(V001_CreateUsersTable::class)
        migrationRegistry.register(V002_CreatePostsTable::class)

        // Migrations de desenvolvimento (dados de teste)
        migrationRegistry.register(V999_InsertTestData::class)
    }
}
```

### Produção

```kotlin
@Configuration
@Profile("prod")
class ProdMigrationConfig(
    private val migrationRegistry: MigrationRegistry
) {

    @PostConstruct
    fun registerMigrations() {
        // Apenas migrations de produção
        migrationRegistry.register(V001_CreateUsersTable::class)
        migrationRegistry.register(V002_CreatePostsTable::class)
    }
}
```

## Status de Migrations

### Endpoint de Status

```kotlin
@RestController
@RequestMapping("/admin/migrations")
class MigrationStatusController(
    private val migrationExecutor: MigrationExecutor,
    private val migrationScanner: MigrationScanner
) {

    @GetMapping("/status")
    fun status(): MigrationStatusResponse {
        val migrations = loadMigrations()
        val status = migrationExecutor.status(migrations)

        return MigrationStatusResponse(
            applied = status.appliedMigrations.map {
                MigrationInfo(it.version, it.description, it.executedAt)
            },
            pending = status.pendingMigrations.map {
                MigrationInfo(it.version, it.description, null)
            }
        )
    }
}

data class MigrationStatusResponse(
    val applied: List<MigrationInfo>,
    val pending: List<MigrationInfo>
)

data class MigrationInfo(
    val version: Int,
    val description: String,
    val executedAt: Instant?
)
```

## Testes

### Configuração para Testes

```kotlin
@SpringBootTest
@ActiveProfiles("test")
class MigrationTest {

    @Autowired
    lateinit var migrationExecutor: MigrationExecutor

    @Test
    fun `should apply all migrations`() {
        val migrations = loadTestMigrations()
        val result = migrationExecutor.migrate(migrations)

        assertThat(result.success).isTrue()
        assertThat(result.totalFailed).isZero()
    }
}
```

### Testcontainers

```kotlin
@SpringBootTest
@Testcontainers
class MigrationIntegrationTest {

    companion object {
        @Container
        val postgres = PostgreSQLContainer("postgres:15")
            .withDatabaseName("test")
    }

    @DynamicPropertySource
    @JvmStatic
    fun properties(registry: DynamicPropertyRegistry) {
        registry.add("aggo.orm.host") { postgres.host }
        registry.add("aggo.orm.port") { postgres.getMappedPort(5432) }
        registry.add("aggo.orm.database") { postgres.databaseName }
        registry.add("aggo.orm.username") { postgres.username }
        registry.add("aggo.orm.password") { postgres.password }
    }

    @Test
    fun `should apply migrations to real database`() {
        // Teste com banco real
    }
}
```

## Exemplo Completo

### Estrutura do Projeto

```
src/main/kotlin/com/example/
├── Application.kt
├── config/
│   └── MigrationConfig.kt
├── migrations/
│   ├── V001_CreateUsersTable.kt
│   ├── V002_CreatePostsTable.kt
│   └── V003_AddCommentsTable.kt
├── domain/
│   ├── User.kt
│   ├── Post.kt
│   └── Comment.kt
└── repository/
    ├── UserRepository.kt
    ├── PostRepository.kt
    └── CommentRepository.kt
```

### Application.kt

```kotlin
package com.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
```

### MigrationConfig.kt

```kotlin
package com.example.config

import com.aggitech.orm.spring.autoconfigure.MigrationRegistry
import com.example.migrations.*
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct

@Configuration
class MigrationConfig(
    private val migrationRegistry: MigrationRegistry
) {

    @PostConstruct
    fun registerMigrations() {
        listOf(
            V001_CreateUsersTable::class,
            V002_CreatePostsTable::class,
            V003_AddCommentsTable::class
        ).forEach { migrationRegistry.register(it) }
    }
}
```

### application.yml

```yaml
spring:
  application:
    name: my-app

aggo:
  orm:
    database: ${DB_NAME:myapp}
    host: ${DB_HOST:localhost}
    port: ${DB_PORT:5432}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD}
    database-type: POSTGRESQL

    migrations:
      enabled: true
      show-details: true
      fail-on-error: true
      validate-checksums: true

logging:
  level:
    com.aggitech.orm: INFO
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Registrar migrations explicitamente
@Configuration
class MigrationConfig(registry: MigrationRegistry) {
    @PostConstruct
    fun register() {
        registry.register(V001_CreateUsersTable::class)
    }
}

// Testar migrations em CI
@Test
fun `migrations should apply successfully`() {
    // ...
}

// Usar ambientes diferentes
# application-prod.yml
aggo.orm.migrations.show-details: false
```

### [AVOID] Evitar

```kotlin
// Não modificar migrations aplicadas em produção
class V001_CreateUsersTable : Migration() {
    // [AVOID] Modificar depois de aplicar em prod
}

// Não desabilitar validação em produção
aggo.orm.migrations.validate-checksums: false  // [AVOID]

// Não fazer rollback automático
aggo.orm.migrations.auto-rollback: true  // [AVOID] Perigoso
```

## Troubleshooting

### Migration não encontrada

```
No migrations found. Please register your migrations.
```

**Solução**: Registre migrations via `MigrationRegistry` ou `@EnableAggoMigrations`.

### Checksum mismatch

```
Migration validation failed: Checksum mismatch
```

**Solução**: Não modifique migrations já aplicadas. Crie nova migration para alterações.

### Falha na conexão

```
Failed to run migrations: Connection refused
```

**Solução**: Verifique se o banco de dados está acessível antes da aplicação iniciar.
