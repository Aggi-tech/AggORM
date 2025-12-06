# Executando Migrations

Este guia explica como executar, validar e reverter migrations.

## MigrationExecutor

O `MigrationExecutor` é a interface principal para gerenciar migrations:

```kotlin
interface MigrationExecutor {
    fun migrate(migrations: List<Migration>): MigrationResult
    fun rollback(steps: Int = 1): MigrationResult
    fun status(migrations: List<Migration>): MigrationStatusReport
    fun validate(migrations: List<Migration>): ValidationResult
}
```

## Configuração

### Setup Manual

```kotlin
import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.migrations.executor.JdbcMigrationExecutor
import com.aggitech.orm.migrations.executor.MigrationScanner
import com.aggitech.orm.migrations.history.JdbcMigrationHistoryRepository
import java.sql.DriverManager

// Configuração do banco
val config = DbConfig(
    database = "myapp",
    user = "postgres",
    password = "password"
)

// Criar conexão
val connection = DriverManager.getConnection(
    config.url,
    config.user,
    config.password
)

// Criar componentes
val historyRepository = JdbcMigrationHistoryRepository(connection, config.dialect)
val executor = JdbcMigrationExecutor(connection, config.dialect, historyRepository)
val scanner = MigrationScanner()
```

### Carregar Migrations

```kotlin
// Lista explícita de migrations
val migrationClasses = listOf(
    V001_CreateUsersTable::class,
    V002_CreatePostsTable::class,
    V003_AddIndexToUsers::class
)

// Carregar instâncias
val migrations = scanner.loadMigrations(migrationClasses)
```

## Executando Migrations

### migrate()

Aplica todas as migrations pendentes:

```kotlin
val result = executor.migrate(migrations)

// Processar resultado
if (result.success) {
    println("Migrations executadas com sucesso!")
    result.executed.forEach { migration ->
        println("[OK] V${migration.version} - ${migration.description} (${migration.executionTimeMs}ms)")
    }
} else {
    println("Falha na execução!")
    result.failed.forEach { migration ->
        println("[FAIL] V${migration.version} - ${migration.description}")
        println("Erro: ${migration.error.message}")
    }
}
```

### MigrationResult

```kotlin
data class MigrationResult(
    val executed: List<ExecutedMigration>,
    val failed: List<FailedMigration>,
    val skipped: List<SkippedMigration>
) {
    val success: Boolean get() = failed.isEmpty()
    val totalExecuted: Int get() = executed.size
    val totalFailed: Int get() = failed.size
    val totalSkipped: Int get() = skipped.size
}

data class ExecutedMigration(
    val version: Int,
    val description: String,
    val executionTimeMs: Long
)

data class FailedMigration(
    val version: Int,
    val description: String,
    val error: Throwable
)

data class SkippedMigration(
    val version: Int,
    val description: String,
    val reason: String
)
```

### Exemplo Completo de Execução

```kotlin
fun runMigrations() {
    val config = DbConfig(
        database = "myapp",
        user = "postgres",
        password = "password"
    )

    val connection = DriverManager.getConnection(
        config.url,
        config.user,
        config.password
    )

    try {
        val historyRepo = JdbcMigrationHistoryRepository(connection, config.dialect)
        val executor = JdbcMigrationExecutor(connection, config.dialect, historyRepo)
        val scanner = MigrationScanner()

        val migrations = scanner.loadMigrations(listOf(
            V001_CreateUsersTable::class,
            V002_CreatePostsTable::class
        ))

        println("Iniciando migrations...")
        println("Total de migrations: ${migrations.size}")

        val result = executor.migrate(migrations)

        println("\n=== Resultado ===")
        println("Executadas: ${result.totalExecuted}")
        println("Ignoradas: ${result.totalSkipped}")
        println("Falhas: ${result.totalFailed}")

        if (result.success) {
            println("\n[OK] Todas as migrations foram aplicadas!")
        } else {
            println("\n[FAIL] Houve falhas na execução")
            System.exit(1)
        }

    } finally {
        connection.close()
    }
}
```

## Rollback

### rollback()

Reverte as últimas N migrations:

```kotlin
// Reverter última migration
val result = executor.rollback()

// Reverter últimas 3 migrations
val result = executor.rollback(steps = 3)

// Processar resultado
result.executed.forEach { migration ->
    println("[ROLLED BACK] V${migration.version} - ${migration.description}")
}
```

### Exemplo de Rollback

```kotlin
fun rollbackMigrations(steps: Int = 1) {
    val connection = getConnection()

    try {
        val historyRepo = JdbcMigrationHistoryRepository(connection, dialect)
        val executor = JdbcMigrationExecutor(connection, dialect, historyRepo)

        println("Revertendo últimas $steps migrations...")

        val result = executor.rollback(steps)

        if (result.success) {
            println("Rollback concluído!")
            result.executed.forEach {
                println("[OK] Revertida: V${it.version} - ${it.description}")
            }
        } else {
            println("Falha no rollback!")
            result.failed.forEach {
                println("[FAIL] V${it.version}: ${it.error.message}")
            }
        }

    } finally {
        connection.close()
    }
}
```

## Status

### status()

Verifica o estado atual das migrations:

```kotlin
val status = executor.status(migrations)

println("=== Status das Migrations ===")
println("Aplicadas: ${status.appliedCount}")
println("Pendentes: ${status.pendingCount}")

println("\n--- Aplicadas ---")
status.appliedMigrations.forEach {
    println("V${it.version} - ${it.description}")
    println("  Executada em: ${it.executedAt}")
    println("  Tempo: ${it.executionTimeMs}ms")
}

println("\n--- Pendentes ---")
status.pendingMigrations.forEach {
    println("V${it.version} - ${it.description}")
}
```

### MigrationStatusReport

```kotlin
data class MigrationStatusReport(
    val appliedCount: Int,
    val pendingCount: Int,
    val appliedMigrations: List<AppliedMigrationInfo>,
    val pendingMigrations: List<PendingMigrationInfo>
)

data class AppliedMigrationInfo(
    val version: Int,
    val description: String,
    val executedAt: Instant,
    val executionTimeMs: Long
)

data class PendingMigrationInfo(
    val version: Int,
    val description: String
)
```

## Validação

### validate()

Valida integridade das migrations:

```kotlin
val validation = executor.validate(migrations)

if (validation.valid) {
    println("Todas as migrations estão válidas!")
} else {
    println("Problemas encontrados:")
    validation.issues.forEach { issue ->
        when (issue) {
            is ValidationIssue.ChecksumMismatch -> {
                println("[CHECKSUM] V${issue.version} - ${issue.description}")
                println("  Esperado: ${issue.expected}")
                println("  Atual: ${issue.actual}")
            }
            is ValidationIssue.MissingMigration -> {
                println("[MISSING] V${issue.version} - ${issue.description}")
            }
            is ValidationIssue.FailedMigration -> {
                println("[FAILED] V${issue.version} - ${issue.description}")
                println("  Erro: ${issue.errorMessage}")
            }
        }
    }
}
```

### Tipos de Problemas

| Tipo | Descrição |
|------|-----------|
| `ChecksumMismatch` | Migration foi modificada após aplicação |
| `MissingMigration` | Migration aplicada não existe mais no código |
| `FailedMigration` | Migration com status FAILED no histórico |

## CLI de Migrations

### Implementação Básica

```kotlin
fun main(args: Array<String>) {
    val command = args.getOrNull(0) ?: "migrate"

    when (command) {
        "migrate" -> runMigrate()
        "rollback" -> runRollback(args.getOrNull(1)?.toIntOrNull() ?: 1)
        "status" -> runStatus()
        "validate" -> runValidate()
        else -> {
            println("Comandos disponíveis:")
            println("  migrate   - Executa migrations pendentes")
            println("  rollback  - Reverte última migration")
            println("  status    - Mostra status das migrations")
            println("  validate  - Valida integridade")
        }
    }
}

fun runMigrate() {
    val executor = createExecutor()
    val migrations = loadMigrations()

    val result = executor.migrate(migrations)

    if (result.success) {
        println("OK: ${result.totalExecuted} migrations executadas")
    } else {
        println("ERRO: ${result.totalFailed} migrations falharam")
        System.exit(1)
    }
}

fun runRollback(steps: Int) {
    val executor = createExecutor()

    val result = executor.rollback(steps)

    if (result.success) {
        println("OK: ${result.totalExecuted} migrations revertidas")
    } else {
        println("ERRO: ${result.totalFailed} rollbacks falharam")
        System.exit(1)
    }
}

fun runStatus() {
    val executor = createExecutor()
    val migrations = loadMigrations()

    val status = executor.status(migrations)

    println("Aplicadas: ${status.appliedCount}")
    println("Pendentes: ${status.pendingCount}")
}

fun runValidate() {
    val executor = createExecutor()
    val migrations = loadMigrations()

    val validation = executor.validate(migrations)

    if (validation.valid) {
        println("OK: Todas as migrations são válidas")
    } else {
        println("ERRO: ${validation.issues.size} problemas encontrados")
        System.exit(1)
    }
}
```

## Transações

### Comportamento Padrão

Cada migration é executada em sua própria transação:

```kotlin
// Se migration falhar, apenas ela é revertida
// Migrations anteriores permanecem aplicadas

// V001 - OK (committed)
// V002 - OK (committed)
// V003 - FAIL (rolled back) <- apenas esta é revertida
```

### Execução em Ordem

Migrations são sempre executadas em ordem de versão:

```kotlin
// Mesmo que listadas fora de ordem
val migrations = listOf(
    V003_AddIndex::class,
    V001_CreateUsers::class,
    V002_CreatePosts::class
)

// Serão executadas como:
// 1. V001_CreateUsers
// 2. V002_CreatePosts
// 3. V003_AddIndex
```

## Histórico

### Tabela de Histórico

O executor cria automaticamente a tabela de histórico:

```sql
CREATE TABLE IF NOT EXISTS aggo_migration_history (
    id SERIAL PRIMARY KEY,
    version INTEGER NOT NULL,
    description VARCHAR(255) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time_ms BIGINT,
    error_message TEXT
);
```

### Consultando Histórico

```kotlin
val historyRepo = JdbcMigrationHistoryRepository(connection, dialect)

// Todas as migrations aplicadas
val history = historyRepo.findAll()

history.forEach { record ->
    println("V${record.version} - ${record.description}")
    println("  Status: ${record.status}")
    println("  Executada: ${record.executedAt}")
    println("  Tempo: ${record.executionTimeMs}ms")
}

// Verificar se específica foi aplicada
val isApplied = historyRepo.isApplied(1)
println("V001 aplicada: $isApplied")
```

## Exemplo Completo

### Script de Deploy

```kotlin
import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.enums.SupportedDatabases
import com.aggitech.orm.migrations.executor.*
import com.aggitech.orm.migrations.history.JdbcMigrationHistoryRepository
import java.sql.DriverManager

object MigrationRunner {

    private val migrations = listOf(
        V001_CreateUsersTable::class,
        V002_CreatePostsTable::class,
        V003_CreateCommentsTable::class,
        V004_AddIndexes::class,
        V005_AddUserRoles::class
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val config = DbConfig(
            database = System.getenv("DB_NAME") ?: "myapp",
            host = System.getenv("DB_HOST") ?: "localhost",
            port = System.getenv("DB_PORT")?.toInt() ?: 5432,
            user = System.getenv("DB_USER") ?: "postgres",
            password = System.getenv("DB_PASSWORD") ?: "password",
            type = SupportedDatabases.POSTGRESQL
        )

        val connection = DriverManager.getConnection(
            config.url,
            config.user,
            config.password
        )

        try {
            val historyRepo = JdbcMigrationHistoryRepository(connection, config.dialect)
            val executor = JdbcMigrationExecutor(connection, config.dialect, historyRepo)
            val scanner = MigrationScanner()

            // Validar primeiro
            val loadedMigrations = scanner.loadMigrations(migrations)
            val validation = executor.validate(loadedMigrations)

            if (!validation.valid) {
                println("ERRO: Validação falhou!")
                validation.issues.forEach { println("  - $it") }
                System.exit(1)
            }

            // Mostrar status
            val status = executor.status(loadedMigrations)
            println("Status atual:")
            println("  Aplicadas: ${status.appliedCount}")
            println("  Pendentes: ${status.pendingCount}")

            if (status.pendingCount == 0) {
                println("\nNenhuma migration pendente.")
                return
            }

            // Executar migrations
            println("\nExecutando ${status.pendingCount} migrations...")

            val result = executor.migrate(loadedMigrations)

            // Resultado
            println("\n=== Resultado ===")

            result.executed.forEach {
                println("[OK] V${it.version} - ${it.description} (${it.executionTimeMs}ms)")
            }

            result.skipped.forEach {
                println("[SKIP] V${it.version} - ${it.description} (${it.reason})")
            }

            result.failed.forEach {
                println("[FAIL] V${it.version} - ${it.description}")
                println("       ${it.error.message}")
            }

            if (result.success) {
                println("\n[OK] Deploy concluído com sucesso!")
            } else {
                println("\n[FAIL] Deploy falhou!")
                System.exit(1)
            }

        } finally {
            connection.close()
        }
    }
}
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Validar antes de executar em produção
val validation = executor.validate(migrations)
if (!validation.valid) {
    throw RuntimeException("Migrations inválidas")
}

// Verificar status antes de deploy
val status = executor.status(migrations)
println("Pendentes: ${status.pendingCount}")

// Usar variáveis de ambiente para credenciais
val config = DbConfig(
    password = System.getenv("DB_PASSWORD")
)
```

### [AVOID] Evitar

```kotlin
// Não ignorar falhas
val result = executor.migrate(migrations)
// [AVOID] Continuar mesmo com falhas

// Não pular validação
executor.migrate(migrations)  // [AVOID] Sem validar

// Não modificar migrations já aplicadas
// [AVOID] Editar V001 após execução em produção
```
