# AggORM Migrations - Guia de Uso

## Migrations Automáticas com Spring Boot

### Setup Básico

#### 1. Adicione a dependência no `build.gradle.kts`

```kotlin
dependencies {
    implementation("com.aggitech.orm:aggo-spring-boot-starter:1.3.0")
    implementation("com.aggitech.orm:aggo-migrations:1.3.0")
}
```

#### 2. Configure no `application.yml`

```yaml
aggo:
  orm:
    database: mydb
    host: localhost
    port: 5432
    username: postgres
    password: secret
    database-type: POSTGRESQL
    dialect: POSTGRESQL

    # Configuração de Migrations (opcional - valores padrão mostrados)
    migrations:
      enabled: true              # Habilita execução automática
      show-details: true         # Mostra detalhes no log
      fail-on-error: true        # Para aplicação se migration falhar
      validate-checksums: true   # Valida checksums de migrations aplicadas
```

#### 3. Crie suas Migrations

Crie migrations no pacote de sua escolha (ex: `com.example.migrations`):

```kotlin
package com.example.migrations

import com.aggitech.orm.migrations.core.Migration
import com.example.entities.User

class V001_20241206_CreateUsersTable : Migration() {

    override fun up() {
        createTable(User::class) {
            column { uuid(User::id).primaryKey().notNull() }
            column { varchar(User::name, 100).notNull() }
            column { varchar(User::email, 255).notNull().unique() }
            column { timestamp(User::createdAt).notNull().default("CURRENT_TIMESTAMP") }
        }
    }

    override fun down() {
        dropTable(User::class)
    }
}
```

#### 4. Registre suas Migrations

**Opção A: Registro Manual (Recomendado)**

```kotlin
package com.example.config

import com.aggitech.orm.spring.autoconfigure.MigrationRegistry
import com.example.migrations.V001_20241206_CreateUsersTable
import com.example.migrations.V002_20241206_CreatePostsTable
import org.springframework.context.annotation.Configuration

@Configuration
class DatabaseMigrationConfig(migrationRegistry: MigrationRegistry) {
    init {
        migrationRegistry.register(V001_20241206_CreateUsersTable::class)
        migrationRegistry.register(V002_20241206_CreatePostsTable::class)
    }
}
```

**Opção B: Scan Automático (Experimental)**

```kotlin
package com.example.config

import com.aggitech.orm.spring.migrations.EnableAggoMigrations
import com.aggitech.orm.spring.migrations.MigrationConfiguration
import org.springframework.context.annotation.Configuration

@Configuration
@EnableAggoMigrations(basePackages = ["com.example.migrations"])
class DatabaseMigrationConfig : MigrationConfiguration()
```

#### 5. Inicie sua Aplicação

As migrations rodarão automaticamente na inicialização:

```
2024-12-06 10:00:00.123  INFO --- AggORM Migrations: Starting migration process...
2024-12-06 10:00:00.234  INFO --- AggORM Migrations: Found 2 migration(s)
2024-12-06 10:00:00.345  INFO --- AggORM Migrations: [OK] V1 - CreateUsersTable (45ms)
2024-12-06 10:00:00.456  INFO --- AggORM Migrations: [OK] V2 - CreatePostsTable (32ms)
2024-12-06 10:00:00.567  INFO --- AggORM Migrations: Completed successfully. Executed: 2, Skipped: 0, Failed: 0
```

## Migrations com JDBC Standalone

Se não estiver usando Spring Boot:

```kotlin
fun main() {
    val connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/mydb", "user", "password")
    val historyRepo = JdbcMigrationHistoryRepository(connection, PostgresDialect)
    val executor = JdbcMigrationExecutor(connection, PostgresDialect, historyRepo)

    val scanner = MigrationScanner()
    val migrations = scanner.loadMigrations(listOf(
        V001_20241206_CreateUsersTable::class,
        V002_20241206_CreatePostsTable::class
    ))

    val result = executor.migrate(migrations)

    if (result.success) {
        println("Migrations executadas com sucesso: ${result.totalExecuted}")
    } else {
        println("Falha: ${result.failed.first().error.message}")
    }
}
```

## DSL Type-Safe

### Criando Tabelas

```kotlin
override fun up() {
    createTable(User::class) {
        // Colunas
        column { uuid(User::id).primaryKey().notNull() }
        column { varchar(User::name, 100).notNull() }
        column { varchar(User::email, 255).notNull().unique() }
        column { text(User::bio) }  // nullable por padrão
        column { timestamp(User::createdAt).notNull().default("CURRENT_TIMESTAMP") }

        // Foreign Keys
        foreignKey(User::companyId, Company::id, Company::class, CascadeType.CASCADE, null)

        // Índices
        index(listOf(User::email), name = "idx_users_email", unique = true)
        index(listOf(User::createdAt), name = "idx_users_created_at")

        // Unique Constraints
        unique(User::email, name = "uk_users_email")
    }
}
```

### Alterando Tabelas

```kotlin
override fun up() {
    transaction {
        updateTable(User::class) {
            // Adicionar coluna
            column { varchar(User::phoneNumber, 20) }

            // Remover coluna
            dropColumn(User::oldField)
        }
    }
}
```

### Foreign Keys - IMPORTANTE

Sempre adicione a coluna ANTES de criar a foreign key:

```kotlin
// CORRETO
createTable(Post::class) {
    column { uuid(Post::id).primaryKey().notNull() }
    column { uuid(Post::userId).notNull() }  // Adiciona a coluna primeiro
    column { varchar(Post::title, 200).notNull() }

    foreignKey(Post::userId, User::id, User::class, CascadeType.CASCADE, null)
}

// ERRADO - Vai gerar erro: "column 'user_id' does not exist"
createTable(Post::class) {
    column { uuid(Post::id).primaryKey().notNull() }
    column { varchar(Post::title, 200).notNull() }

    foreignKey(Post::userId, User::id, User::class, CascadeType.CASCADE, null)  // userId não existe!
}
```

## Tipos de Colunas Suportados

```kotlin
// Strings
varchar(property, length)
char(property, length)
text(property)

// Números
integer(property)
bigInteger(property)
smallInteger(property)
decimal(property, precision, scale)
float(property)
double(property)

// Datas
date(property)
time(property)
timestamp(property)

// Outros
boolean(property)
uuid(property)
json(property)
jsonb(property)
binary(property, length)
blob(property)
```

## Modificadores de Coluna

```kotlin
column { uuid(User::id).notNull().primaryKey() }
column { varchar(User::email, 255).notNull().unique() }
column { bigInteger(User::id).autoIncrement() }
column { timestamp(User::createdAt).default("CURRENT_TIMESTAMP") }
column { varchar(User::status, 20).default("'active'") }
```

## Cascade Types

```kotlin
CascadeType.CASCADE       // Deleta/atualiza registros relacionados
CascadeType.SET_NULL      // Define como NULL
CascadeType.RESTRICT      // Impede operação
CascadeType.NO_ACTION     // Não faz nada
CascadeType.SET_DEFAULT   // Define valor padrão
```

## Desabilitando Migrations

Para desabilitar temporariamente (útil em testes):

```yaml
aggo:
  orm:
    migrations:
      enabled: false
```

Ou via variável de ambiente:

```bash
AGGO_ORM_MIGRATIONS_ENABLED=false java -jar app.jar
```

## Troubleshooting

### Erro: "column 'xxx' referenced in foreign key constraint does not exist"

**Causa**: Você está tentando criar uma foreign key para uma coluna que não foi adicionada à tabela.

**Solução**: Adicione a coluna ANTES da foreign key:

```kotlin
column { uuid(Post::userId).notNull() }  // Adicione isto
foreignKey(Post::userId, User::id, User::class, CascadeType.CASCADE, null)
```

### Erro: "No migrations found"

**Causa**: As migrations não foram registradas no `MigrationRegistry`.

**Solução**: Crie uma classe de configuração e registre suas migrations:

```kotlin
@Configuration
class DatabaseMigrationConfig(migrationRegistry: MigrationRegistry) {
    init {
        migrationRegistry.register(V001_CreateUsersTable::class)
    }
}
```

### Erro: "Migration validation failed: Checksum mismatch"

**Causa**: Uma migration que já foi aplicada foi modificada.

**Solução**:
- Nunca modifique migrations já aplicadas
- Crie uma nova migration para fazer as alterações necessárias
- Se estiver em desenvolvimento e quiser resetar, delete a tabela `aggo_migration_history`

### Erro: "Transaction not active"

**Causa**: Operações de migration fora de transação.

**Solução**: As migrations são automaticamente executadas em transações. Se precisar de transação explícita:

```kotlin
override fun up() {
    transaction {
        // operações aqui
    }
}
```

## Boas Práticas

1. **Nomenclatura**: Use o padrão `V{versão}_{timestamp}_{descrição}`
   - Exemplo: `V001_20241206_CreateUsersTable`

2. **Versionamento**: Nunca modifique migrations já aplicadas em produção

3. **Reversibilidade**: Sempre implemente `down()` corretamente

4. **Atomicidade**: Cada migration deve fazer uma única mudança lógica

5. **Testes**: Teste suas migrations em ambiente de desenvolvimento antes de aplicar em produção

6. **Foreign Keys**: Sempre adicione a coluna antes de criar a foreign key

7. **Índices**: Adicione índices para colunas frequentemente consultadas

8. **Defaults**: Use valores padrão para facilitar migrations de dados existentes

## Comandos CLI (Futuro)

Em desenvolvimento:

```bash
# Executar migrations
./gradlew aggMigrate

# Ver status
./gradlew aggMigrateStatus

# Gerar nova migration
./gradlew aggMigrateGenerate --name=CreateUsersTable

# Rollback
./gradlew aggMigrateRollback --steps=1
```

## Referências

- [README.md](README.md) - Documentação completa do módulo
- [MIGRATION_TYPE_SAFE_SPEC.md](MIGRATION_TYPE_SAFE_SPEC.md) - Especificação técnica
- [Spring Boot Starter README](../aggo-spring-boot-starter/README.md) - Integração com Spring Boot
