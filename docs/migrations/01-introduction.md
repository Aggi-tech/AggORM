# Migrations - Introdução

O módulo de migrations do AggORM fornece um sistema type-safe para gerenciar alterações de schema do banco de dados.

## Visão Geral

Migrations são scripts versionados que descrevem alterações no schema do banco de dados. O AggORM oferece uma DSL Kotlin type-safe para criar migrations, eliminando erros de digitação e fornecendo auto-complete.

## Conceitos Básicos

### O que é uma Migration?

Uma migration é uma classe Kotlin que define duas operações:

- **up()**: Aplica a alteração (ex: criar tabela)
- **down()**: Reverte a alteração (ex: dropar tabela)

```kotlin
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

### Convenção de Nomenclatura

Migrations seguem o padrão: `V{versão}_{timestamp}_{descrição}`

```
V001_20241206120000_CreateUsersTable
V002_20241206120100_CreatePostsTable
V003_20241206120200_AddIndexToUsers
```

Componentes:
- **V{versão}**: Número sequencial da versão (V001, V002, ...)
- **{timestamp}**: Data/hora da criação (yyyyMMddHHmmss)
- **{descrição}**: Descrição em CamelCase

### Versionamento

O sistema mantém uma tabela de histórico (`aggo_migration_history`) que registra:

- Versão da migration
- Descrição
- Checksum (hash do conteúdo)
- Data de execução
- Tempo de execução
- Status (SUCCESS, FAILED, ROLLED_BACK)

## Fluxo de Trabalho

### 1. Criar Migration

```kotlin
class V001_CreateUsersTable : Migration() {
    override fun up() {
        createTable<User> {
            column(User::id).bigInteger().primaryKey().autoIncrement()
            column(User::name).varchar(100).notNull()
        }
    }

    override fun down() {
        dropTable<User>()
    }
}
```

### 2. Registrar Migration

```kotlin
val migrations = listOf(
    V001_CreateUsersTable::class,
    V002_CreatePostsTable::class
)
```

### 3. Executar Migrations

```kotlin
val result = executor.migrate(migrations)

println("Executadas: ${result.totalExecuted}")
println("Ignoradas: ${result.totalSkipped}")
println("Falhas: ${result.totalFailed}")
```

### 4. Verificar Status

```kotlin
val status = executor.status(migrations)

println("Aplicadas: ${status.appliedCount}")
println("Pendentes: ${status.pendingCount}")

status.pendingMigrations.forEach {
    println("Pendente: V${it.version} - ${it.description}")
}
```

## Operações Suportadas

### Tabelas

| Operação | Descrição |
|----------|-----------|
| `createTable<T>` | Cria nova tabela |
| `dropTable<T>` | Remove tabela |
| `renameTable` | Renomeia tabela |

### Colunas

| Operação | Descrição |
|----------|-----------|
| `addColumn` | Adiciona coluna |
| `dropColumn` | Remove coluna |
| `renameColumn` | Renomeia coluna |

### Índices

| Operação | Descrição |
|----------|-----------|
| `createIndex` | Cria índice |
| `dropIndex` | Remove índice |

### SQL Raw

| Operação | Descrição |
|----------|-----------|
| `executeSql` | Executa SQL customizado |

## Tipos de Dados

### Tipos Suportados

| Tipo | Método | SQL PostgreSQL |
|------|--------|----------------|
| UUID | `uuid()` | `UUID` |
| String | `varchar(n)` | `VARCHAR(n)` |
| String | `char(n)` | `CHAR(n)` |
| String | `text()` | `TEXT` |
| Int | `integer()` | `INTEGER` |
| Long | `bigInteger()` | `BIGINT` |
| Short | `smallInteger()` | `SMALLINT` |
| Double | `double()` | `DOUBLE PRECISION` |
| Float | `float()` | `REAL` |
| BigDecimal | `decimal(p, s)` | `DECIMAL(p, s)` |
| Boolean | `boolean()` | `BOOLEAN` |
| Date | `date()` | `DATE` |
| Time | `time()` | `TIME` |
| Timestamp | `timestamp()` | `TIMESTAMP` |
| ByteArray | `binary(n)` | `BYTEA` |
| ByteArray | `blob()` | `BYTEA` |
| JSON | `json()` | `JSON` |
| JSON | `jsonb()` | `JSONB` |

### Constraints

| Constraint | Método |
|------------|--------|
| Primary Key | `primaryKey()` |
| Not Null | `notNull()` |
| Unique | `unique()` |
| Auto Increment | `autoIncrement()` |
| Default | `default(value)` |
| Foreign Key | `foreignKey<T>(...)` |

## Type Safety

### Referências de Propriedades

A DSL usa `KProperty1` para referências type-safe:

```kotlin
// [OK] Compila - propriedade existe
column(User::name).varchar(100)

// [AVOID] Não compila - propriedade não existe
column(User::nonExistent).varchar(100)

// [OK] Tipo inferido automaticamente
column(User::age).integer()  // age: Int -> INTEGER
```

### Benefícios

1. **Erros em tempo de compilação**: Propriedades inválidas são detectadas
2. **Refactoring seguro**: Renomear propriedades atualiza migrations
3. **Auto-complete**: IDE sugere propriedades disponíveis
4. **Documentação**: Tipos são auto-documentados

## Histórico de Migrations

### Tabela de Histórico

```sql
CREATE TABLE aggo_migration_history (
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

### Status de Migration

| Status | Descrição |
|--------|-----------|
| `SUCCESS` | Migration executada com sucesso |
| `FAILED` | Migration falhou |
| `ROLLED_BACK` | Migration foi revertida |

### Checksum

O checksum é um hash SHA-256 do conteúdo da migration. Ele é usado para:

- Detectar modificações em migrations já aplicadas
- Garantir integridade do histórico
- Validar consistência entre ambientes

## Exemplo Prático

### Modelo de Dados

```kotlin
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val passwordHash: String,
    val active: Boolean,
    val createdAt: Timestamp
)

data class Post(
    val id: Long,
    val userId: Long,
    val title: String,
    val content: String,
    val publishedAt: Timestamp?
)
```

### Migrations

```kotlin
// V001 - Criar tabela users
class V001_CreateUsersTable : Migration() {
    override fun up() {
        createTable<User> {
            column(User::id).bigInteger().primaryKey().autoIncrement()
            column(User::name).varchar(100).notNull()
            column(User::email).varchar(255).notNull().unique()
            column(User::passwordHash).varchar(255).notNull()
            column(User::active).boolean().notNull().default("true")
            column(User::createdAt).timestamp().notNull().default("CURRENT_TIMESTAMP")
        }
    }

    override fun down() {
        dropTable<User>()
    }
}

// V002 - Criar tabela posts
class V002_CreatePostsTable : Migration() {
    override fun up() {
        createTable<Post> {
            column(Post::id).bigInteger().primaryKey().autoIncrement()
            column(Post::userId).bigInteger().notNull()
            column(Post::title).varchar(200).notNull()
            column(Post::content).text().notNull()
            column(Post::publishedAt).timestamp()

            foreignKey(Post::userId)
                .references<User>(User::id)
                .onDelete(CascadeType.CASCADE)
        }
    }

    override fun down() {
        dropTable<Post>()
    }
}

// V003 - Adicionar índice
class V003_AddIndexToPostsTitle : Migration() {
    override fun up() {
        createIndex<Post>("idx_posts_title") {
            columns(Post::title)
        }
    }

    override fun down() {
        dropIndex("idx_posts_title")
    }
}
```

### Executando

```kotlin
fun main() {
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

    val historyRepo = JdbcMigrationHistoryRepository(connection, config.dialect)
    val executor = JdbcMigrationExecutor(connection, config.dialect, historyRepo)
    val scanner = MigrationScanner()

    val migrations = scanner.loadMigrations(listOf(
        V001_CreateUsersTable::class,
        V002_CreatePostsTable::class,
        V003_AddIndexToPostsTitle::class
    ))

    val result = executor.migrate(migrations)

    result.executed.forEach {
        println("[OK] V${it.version} - ${it.description} (${it.executionTimeMs}ms)")
    }

    result.skipped.forEach {
        println("[SKIP] V${it.version} - ${it.description}")
    }

    result.failed.forEach {
        println("[FAIL] V${it.version} - ${it.description}: ${it.error.message}")
    }
}
```

## Próximos Passos

- [Criando Migrations](./02-creating-migrations.md) - DSL detalhada
- [Executando Migrations](./03-running-migrations.md) - Executor e rollback
