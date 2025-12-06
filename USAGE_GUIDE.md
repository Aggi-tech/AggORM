# AggORM - Guia de Uso

## Visão Geral

AggORM é um ORM leve e tipado para Kotlin que combina o poder do JDBC com a elegância das coroutines. Ele oferece uma DSL fluente e type-safe para operações de banco de dados.

## Características Principais

- **Type-Safe**: Totalmente tipado usando Kotlin Reflection e Reified Types
- **Coroutines**: Suporte nativo a programação assíncrona com Kotlin Coroutines
- **JDBC**: Baseado em JDBC para máxima compatibilidade e performance
- **DSL Fluente**: Interface intuitiva e legível para construir queries
- **SQL Injection Protection**: Prepared Statements automáticos
- **Transações**: Suporte completo a transações com rollback automático
- **Connection Management**: Gerenciamento automático de recursos com `use`

## Configuração

### 1. Adicione as dependências no `build.gradle.kts`:

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation(kotlin("reflect"))
    // Adicione o driver JDBC do seu banco
    implementation("org.postgresql:postgresql:42.7.1") // Para PostgreSQL
    // ou
    implementation("mysql:mysql-connector-java:8.0.33") // Para MySQL
}
```

### 2. Configure a conexão:

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

## Melhores Práticas

### 1. Use Coroutines para Operações Assíncronas

```kotlin
// [AVOID] Evite operações síncronas no thread principal
val users = select<User>(connectionFactory).execute()

// [OK] Prefira operações assíncronas
val users = select<User>(connectionFactory).executeAsync()
```

### 2. Sempre Use Prepared Statements

O AggORM usa Prepared Statements automaticamente, mas sempre passe parâmetros corretamente:

```kotlin
// [AVOID] NUNCA faça string interpolation em SQL
val email = userInput
select<User>(connectionFactory)
    .where("email = '$email'") // VULNERÁVEL A SQL INJECTION!

// [OK] Sempre use parâmetros
select<User>(connectionFactory)
    .where("email = ?", email) // Seguro
```

### 3. Use o Connection Factory com Use Block

O `use` garante que as conexões sejam fechadas automaticamente:

```kotlin
// [OK] O framework já usa `use` internamente
connectionFactory.open().use { connection ->
    // Sua operação aqui
} // Conexão fechada automaticamente
```

### 4. Use Repository Pattern

Organize seu código separando a lógica de acesso a dados:

```kotlin
class UserRepository(private val connectionFactory: JdbcConnectionFactory) {

    suspend fun findById(id: Long): User? {
        return select<User>(connectionFactory)
            .where("id = ?", id)
            .executeFirstAsync()
    }

    suspend fun create(user: User): Long {
        return insert(user, connectionFactory).executeAsync()
    }
}
```

### 5. Use Transações para Operações Múltiplas

```kotlin
// [OK] Garante atomicidade
transaction(connectionFactory) {
    insert(user1, connectionFactory).executeAsync()
    insert(user2, connectionFactory).executeAsync()
    // Se qualquer operação falhar, todas são revertidas
}
```

### 6. Use Connection Pooling em Produção

```kotlin
// Para produção, considere usar HikariCP
val hikariConfig = HikariConfig().apply {
    jdbcUrl = config.url
    username = config.user
    password = config.password
    maximumPoolSize = 10
}
val dataSource = HikariDataSource(hikariConfig)
```

## Exemplos de Uso

### SELECT Queries

```kotlin
// SELECT simples
val users = select<User>(connectionFactory).executeAsync()

// SELECT com filtros
val adults = select<User>(connectionFactory)
    .where("age >= ?", 18)
    .executeAsync()

// SELECT com múltiplos filtros
val results = select<User>(connectionFactory)
    .where("age >= ? AND email LIKE ?", 18, "%@gmail.com")
    .orderBy("name", "ASC")
    .limit(10)
    .executeAsync()

// SELECT retornando um único resultado
val user = select<User>(connectionFactory)
    .where("id = ?", 1)
    .executeFirstAsync()
```

### INSERT Operations

```kotlin
val newUser = User(
    name = "John Doe",
    email = "john@example.com",
    age = 30
)

// Retorna o ID gerado
val userId = insert(newUser, connectionFactory).executeAsync()
```

### UPDATE Operations

```kotlin
val updatedRows = update<User>(connectionFactory)
    .set("email", "newemail@example.com")
    .set("age", 31)
    .where("id = ?", userId)
    .executeAsync()
```

### DELETE Operations

```kotlin
val deletedRows = delete<User>(connectionFactory)
    .where("id = ?", userId)
    .executeAsync()
```

### Transações

```kotlin
try {
    transaction(connectionFactory) {
        val userId = insert(user, connectionFactory).executeAsync()
        update<User>(connectionFactory)
            .set("status", "active")
            .where("id = ?", userId)
            .executeAsync()
    }
} catch (e: Exception) {
    println("Transaction failed: ${e.message}")
}
```

## Definindo Entidades

As entidades devem ser data classes com um primary constructor:

```kotlin
data class User(
    val id: Long? = null,      // ID opcional para novos registros
    val name: String,
    val email: String,
    val age: Int,
    val createdAt: java.time.LocalDateTime? = null
)
```

### Convenções:

- O nome da classe em lowercase será usado como nome da tabela
- Os nomes das propriedades correspondem aos nomes das colunas
- O campo `id` é tratado especialmente em INSERT operations

## Tratamento de Erros

```kotlin
try {
    val users = select<User>(connectionFactory)
        .where("invalid_column = ?", "value")
        .executeAsync()
} catch (e: SQLException) {
    // Trate erros SQL específicos
    println("Database error: ${e.message}")
} catch (e: Exception) {
    // Trate outros erros
    println("Error: ${e.message}")
}
```

## Performance Tips

1. **Use Connection Pooling**: Em produção, sempre use um connection pool (HikariCP, C3P0, etc.)

2. **Use Batch Operations**: Para múltiplas inserções, use transações:
```kotlin
transaction(connectionFactory) {
    users.forEach { user ->
        insert(user, connectionFactory).executeAsync()
    }
}
```

3. **Use LIMIT**: Sempre limite o número de resultados quando apropriado:
```kotlin
select<User>(connectionFactory).limit(100).executeAsync()
```

4. **Use Dispatchers.IO**: O framework já usa `Dispatchers.IO` internamente para operações de I/O

5. **Feche Recursos**: O framework gerencia isso automaticamente com `use`

## Segurança

### SQL Injection Protection

O AggORM protege automaticamente contra SQL Injection usando Prepared Statements:

```kotlin
// [OK] Seguro - usa prepared statements
select<User>(connectionFactory)
    .where("email = ? AND age > ?", userEmail, minAge)
    .executeAsync()
```

### Validação de Dados

Sempre valide dados no service layer antes de persistir:

```kotlin
class UserService(private val repository: UserRepository) {
    suspend fun createUser(name: String, email: String, age: Int): Long {
        require(age >= 18) { "User must be 18 or older" }
        require(email.contains("@")) { "Invalid email" }

        return repository.create(User(name = name, email = email, age = age))
    }
}
```

## Bancos de Dados Suportados

- PostgreSQL (porta padrão: 5432)
- MySQL (porta padrão: 3306)

Para adicionar suporte a outros bancos, estenda o enum `SupportedDatabases`.

## Troubleshooting

### Erro: "Entity must have a primary constructor"

Certifique-se de que sua entidade é uma data class com um primary constructor.

### Erro: "Parameter name not found"

Habilite o plugin Kotlin Reflection e certifique-se de que os nomes dos parâmetros estejam preservados.

### Erro: Connection timeout

Verifique suas configurações de connection pool e aumente o timeout se necessário.

## Exemplos Completos

Veja o arquivo `src/main/kotlin/com/aggitech/orm/examples/UsageExamples.kt` para exemplos completos de uso.
