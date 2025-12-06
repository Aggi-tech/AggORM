# AggORM Migrations Module

Sistema completo de migrations para o AggORM, permitindo gerenciamento type-safe de mudanças de schema de banco de dados.

## Status da Implementacao

Phase 6: History Tracking - Sistema de rastreamento de migrations aplicadas
Phase 4: SQL Rendering - Geracao de SQL especifico para PostgreSQL
Phase 5: Migration Executor - Execucao de migrations com suporte a transacoes
Phase 7: Code Generator - Geracao automatica de codigo Kotlin para migrations

## Estrutura do Módulo

```
aggo-migrations/
├── src/main/kotlin/com/aggitech/orm/migrations/
│   ├── core/                 # Classes base e operações
│   │   ├── Migration.kt      # Classe base abstrata para migrations
│   │   ├── MigrationOperation.kt  # Operações (CreateTable, AddColumn, etc.)
│   │   └── MigrationException.kt  # Exceções
│   ├── dsl/                  # DSL para definição de migrations
│   │   └── ColumnType.kt     # Tipos de colunas SQL
│   ├── history/              # Rastreamento de histórico
│   │   ├── MigrationHistory.kt         # Data classes de histórico
│   │   ├── MigrationHistoryRepository.kt  # Interface do repositório
│   │   └── JdbcMigrationHistoryRepository.kt  # Implementação JDBC
│   ├── renderer/             # Renderização de SQL
│   │   ├── MigrationRenderer.kt        # Interface
│   │   ├── PostgresMigrationRenderer.kt  # Implementação PostgreSQL
│   │   └── MigrationRendererFactory.kt   # Factory
│   ├── executor/             # Execução de migrations
│   │   ├── MigrationExecutor.kt      # Interface
│   │   ├── JdbcMigrationExecutor.kt  # Implementação JDBC
│   │   └── MigrationScanner.kt       # Scanner de migrations
│   └── generator/            # Geração de código
│       ├── MigrationGenerator.kt       # Interface
│       ├── KotlinMigrationGenerator.kt # Implementação
│       └── MigrationGeneratorCli.kt    # CLI helper
└── src/test/kotlin/          # Testes com TestContainers

```

## Funcionalidades Implementadas

### 1. History Tracking (Phase 6)

Rastreamento completo de migrations aplicadas com:
- Tabela `aggo_migration_history` criada automaticamente
- Armazenamento de: version, timestamp, description, checksum, status, execution time
- Validação de checksums para detectar modificações pós-aplicação
- Suporte a JDBC (R2DBC como interface para implementação futura)

**Arquivos:**
- `MigrationHistory.kt` - Data classes (MigrationRecord, MigrationStatus)
- `MigrationHistoryRepository.kt` - Interface do repositório
- `JdbcMigrationHistoryRepository.kt` - Implementação com PreparedStatements
- `MigrationHistoryRepositoryTest.kt` - 8 testes com TestContainers PostgreSQL

### 2. SQL Rendering (Phase 4)

Conversão de operações de migration para SQL específico do PostgreSQL:
- **12 tipos de operações** suportadas: CreateTable, DropTable, AddColumn, DropColumn, AlterColumn, RenameColumn, RenameTable, CreateIndex, DropIndex, AddForeignKey, DropForeignKey, ExecuteSql
- **18+ tipos SQL**: VARCHAR, TEXT, INTEGER, BIGINT, BOOLEAN, DECIMAL, TIMESTAMP, UUID, JSON, JSONB, etc.
- **Peculiaridades PostgreSQL**:
  - Auto-increment: `GENERATED ALWAYS AS IDENTITY`
  - Identifier quoting: `"table_name"`
  - ALTER COLUMN com múltiplos statements separados
  - Foreign keys como ALTER TABLE separado

**Arquivos:**
- `MigrationRenderer.kt` - Interface
- `PostgresMigrationRenderer.kt` - 300+ linhas de renderização PostgreSQL
- `MigrationRendererFactory.kt` - Factory pattern
- `PostgresMigrationRendererTest.kt` - 12 testes unitários

### 3. Migration Executor (Phase 5)

Orquestração completa de execução com:
- **Transaction safety**: Cada migration em transação única
- **Rollback automático**: Em caso de falha
- **Checksum validation**: Previne execução de migrations modificadas
- **Migration status**: Applied, pending, failed migrations
- **Validation**: Detecta checksums modificados e migrations faltantes
- **Rollback support**: Execução de down() com instanciação via reflection

**Arquivos:**
- `Migration.kt` - Classe base com DSL helpers e cálculo de checksum
- `MigrationExecutor.kt` - Interface com 4 métodos principais
- `JdbcMigrationExecutor.kt` - Implementação JDBC completa (250+ linhas)
- `MigrationScanner.kt` - Carregamento de migrations (lista explícita)
- `JdbcMigrationExecutorTest.kt` - 7 testes de integração

### 4. Code Generator (Phase 7)

Geração automática de arquivos Kotlin de migration:
- **Versionamento automático**: V001, V002, etc.
- **Timestamps**: yyyyMMddHHmmss
- **Naming pattern**: `V{version}_{timestamp}_{description}`
- **Reversible operations**: up() e down() gerados automaticamente
- **Package customizável**: Default "db.migrations"
- **CLI helpers**: createMigration, createTableMigration, addColumnMigration

**Arquivos:**
- `MigrationGenerator.kt` - Interface
- `KotlinMigrationGenerator.kt` - Gerador de código Kotlin
- `MigrationGeneratorCli.kt` - CLI com helpers
- `KotlinMigrationGeneratorTest.kt` - 8 testes unitários

## Exemplo de Uso

### 1. Criar uma Migration Manualmente

```kotlin
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val createdAt: LocalDateTime
)

class V001_20231201_CreateUsers : Migration() {
    override fun up() {
        createTable("users") {
            column { bigInteger(User::id).primaryKey().autoIncrement() }
            column { varchar(User::name, 100).notNull() }
            column { varchar(User::email, 255).notNull().unique() }
            column { timestamp(User::createdAt).notNull().default("CURRENT_TIMESTAMP") }
        }

        createIndex("users", listOf("email"), unique = true)
    }

    override fun down() {
        dropTable("users")
    }
}
```

### 2. Executar Migrations

```kotlin
val connection = DriverManager.getConnection(jdbcUrl, username, password)
val historyRepo = JdbcMigrationHistoryRepository(connection, PostgresDialect)
val executor = JdbcMigrationExecutor(connection, PostgresDialect, historyRepo)

val scanner = MigrationScanner()
val migrations = scanner.loadMigrations(listOf(
    V001_20231201_CreateUsers::class,
    V002_20231202_CreatePosts::class
))

val result = executor.migrate(migrations)

if (result.success) {
    println("Executed ${result.totalExecuted} migrations")
} else {
    println("Failed: ${result.failed.first().error.message}")
}
```

### 3. Gerar Migration Automaticamente

```kotlin
data class Product(
    val id: Long,
    val name: String,
    val price: BigDecimal
)

MigrationGeneratorCli.createMigration(
    description = "CreateProductsTable",
    migrationOutputPath = "src/main/kotlin/db/migrations"
) { builder ->
    builder.addOperation(
        description = "Create products table",
        upCode = """
            createTable("products") {
                column { bigInteger(Product::id).primaryKey().autoIncrement() }
                column { varchar(Product::name, 200).notNull() }
                column { decimal(Product::price, 10, 2).notNull() }
            }
        """.trimIndent(),
        downCode = """dropTable("products")"""
    )
}
```

### 4. Status de Migrations

```kotlin
val status = executor.status(migrations)

println("Applied: ${status.appliedCount}")
println("Pending: ${status.pendingCount}")

status.pendingMigrations.forEach {
    println("  - V${it.version}: ${it.description}")
}
```

### 5. Rollback

```kotlin
val result = executor.rollback(steps = 1)  // Rollback da última migration
```

## Testes

Todos os componentes possuem testes abrangentes usando **TestContainers PostgreSQL 15**:

- **History**: 8 testes (CRUD, checksums, validação)
- **Renderer**: 12 testes (todas operações, tipos SQL)
- **Executor**: 7 testes (execução, rollback, transações, checksums)
- **Generator**: 8 testes (geração, versionamento, reversibilidade)

Para executar os testes:

```bash
./gradlew :aggo-migrations:test
```

## Arquitetura de Decisões

### 1. History First
Implementar history tracking antes do executor define contratos claros.

### 2. PostgreSQL Priority
Foco em PostgreSQL completamente antes de MySQL.

### 3. Transaction Safety
Toda migration executa em transação única com rollback automático.

### 4. Immutable Migrations
Migrations não podem ser modificadas após aplicação (checksum validation).

### 5. Explicit Registration
Lista explícita de migrations ao invés de classpath scanning (simplicidade).

### 6. JDBC Priority
JDBC completamente implementado, R2DBC como interface futura.

## Limitações e TODOs

### Implementado nesta branch:
- History tracking completo
- SQL rendering para PostgreSQL
- Migration executor com transações
- Code generator básico
- Testes com TestContainers

### Não implementado (fora do escopo):
- R2DBC executor (interface criada, implementação pendente)
- MySQL renderer (factory lança exceção)
- Scanner com classpath scanning (somente lista explícita)
- Integration com detector/scanner de entidades (Phase 2-3 da feature/migrations-system branch)
- Gradle plugin (Phase 8)
- Spring Boot auto-configuration (Phase 9)

## Dependências

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":aggo-core"))
    implementation(kotlin("reflect"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")

    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("org.postgresql:postgresql:42.7.2")
}
```

## Integração Futura

Este módulo pode ser integrado ao build principal adicionando ao `settings.gradle.kts`:

```kotlin
include("aggo-migrations")
```

Porém, mantém-se separado por enquanto conforme solicitado no plano.

## Próximos Passos Recomendados

1. **MySQL Support**: Implementar MySqlMigrationRenderer
2. **R2DBC Executor**: Implementar versão reativa
3. **Entity Scanner Integration**: Conectar com o scanner de entidades da feature/migrations-system
4. **Gradle Plugin**: Tasks `generateMigration`, `migrate`, `rollback`, `status`
5. **Concurrent Safety**: Implementar locks com pg_advisory_lock
6. **Dry Run**: Modo de visualização de SQL sem executar

## Licença

MIT License - Parte do projeto AggORM
