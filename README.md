# AggORM - Kotlin ORM Declarativo

**AggORM** é um ORM leve, type-safe e declarativo para Kotlin que combina a performance do JDBC com a elegância das coroutines e uma DSL expressiva.

## Características Principais

- **DSL Declarativa**: Sintaxe expressiva e legível inspirada em query builders modernos
- **Type-Safe**: Totalmente tipado usando Kotlin Reflection e Property References
- **Coroutines**: Suporte nativo a programação assíncrona com Kotlin Coroutines
- **JDBC**: Baseado em JDBC para máxima compatibilidade e performance
- **SQL Injection Protection**: Prepared Statements automáticos em todas as operações
- **Transações**: Suporte completo a transações com rollback automático
- **Join Support**: Suporte a INNER, LEFT, RIGHT e FULL JOIN

## Instalação

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation(kotlin("reflect"))

    // Driver JDBC (escolha o seu banco)
    implementation("org.postgresql:postgresql:42.7.1") // PostgreSQL
    // ou
    implementation("mysql:mysql-connector-java:8.0.33") // MySQL
}
```

## Configuração

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
val users = query<User>(connectionFactory) {
    from(User::class)
    select {
        +User::name
        +User::email
        +User::age
    }
}.execute()

// Query com WHERE
val adults = query<User>(connectionFactory) {
    from(User::class)
    select { all() }
    where {
        User::age gte 18
        User::email like "%@gmail.com"
    }
}.execute()

// Query com ORDER BY
val sorted = query<User>(connectionFactory) {
    from(User::class)
    select { all() }
    orderBy {
        User::name.asc()
        User::age.desc()
    }
}.execute()

// Query com JOIN
val usersWithCities = query<User>(connectionFactory) {
    from(User::class)
    select { all() }
    leftJoin {
        table(City::class)
        on(User::cityId, City::id)
    }
    where {
        City::country eq "Brazil"
    }
}.execute()

// Paginação
val paged = query<User>(connectionFactory) {
    from(User::class)
    select { all() }
    limit(10)
    offset(20)
}.execute()
```

### Operadores WHERE Suportados

```kotlin
where {
    User::age eq 18           // =
    User::age ne 18           // !=
    User::age gt 18           // >
    User::age gte 18          // >=
    User::age lt 18           // <
    User::age lte 18          // <=
    User::email like "%@gmail.com"  // LIKE
    User::id isIn listOf(1, 2, 3)   // IN
    User::email.isNull()       // IS NULL
    User::email.isNotNull()    // IS NOT NULL
    raw("age BETWEEN ? AND ?", 18, 65)  // SQL customizado
}
```

### INSERT Operations

```kotlin
// INSERT com valores explícitos
val userId = insert<User>(connectionFactory) {
    into(User::class)
    set {
        User::name to "John Doe"
        User::email to "john@example.com"
        User::age to 30
    }
}.execute()

// INSERT usando uma entidade
val newUser = User(name = "Jane", email = "jane@example.com", age = 25)
val id = insert<User>(connectionFactory) {
    into(User::class)
    values(newUser)
}.execute()
```

### UPDATE Operations

```kotlin
val updatedRows = update<User>(connectionFactory) {
    table(User::class)
    set {
        User::email to "newemail@example.com"
        User::age to 31
    }
    where {
        User::id eq userId
    }
}.execute()
```

### DELETE Operations

```kotlin
val deletedRows = delete<User>(connectionFactory) {
    from(User::class)
    where {
        User::id eq userId
    }
}.execute()
```

### Transações

```kotlin
transaction(connectionFactory) {
    // Múltiplas operações
    val userId = insert<User>(connectionFactory) {
        into(User::class)
        set {
            User::name to "Transaction Test"
            User::email to "test@example.com"
            User::age to 25
        }
    }.execute()

    insert<Order>(connectionFactory) {
        into(Order::class)
        set {
            Order::userId to userId
            Order::product to "Product A"
            Order::amount to 99.99
        }
    }.execute()

    // Se qualquer operação falhar, tudo é revertido
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

## Repository Pattern

```kotlin
class UserRepository(private val connectionFactory: JdbcConnectionFactory) {

    suspend fun findAll(): List<User> = query<User>(connectionFactory) {
        from(User::class)
        select { all() }
    }.execute()

    suspend fun findById(id: Long): User? = query<User>(connectionFactory) {
        from(User::class)
        select { all() }
        where {
            User::id eq id
        }
    }.executeFirst()

    suspend fun create(user: User): Long = insert<User>(connectionFactory) {
        into(User::class)
        values(user)
    }.execute()

    suspend fun update(id: Long, name: String, email: String): Int =
        update<User>(connectionFactory) {
            table(User::class)
            set {
                User::name to name
                User::email to email
            }
            where {
                User::id eq id
            }
        }.execute()

    suspend fun delete(id: Long): Int = delete<User>(connectionFactory) {
        from(User::class)
        where {
            User::id eq id
        }
    }.execute()
}
```

## Service Layer

```kotlin
class UserService(private val repository: UserRepository) {

    suspend fun registerUser(name: String, email: String, age: Int): Long {
        require(age >= 18) { "User must be 18 or older" }

        val existingUser = repository.findByEmail(email)
        if (existingUser != null) {
            throw IllegalStateException("Email already registered")
        }

        val user = User(name = name, email = email, age = age)
        return repository.create(user)
    }
}
```

## Recursos Avançados

### Debug SQL

```kotlin
val sqlQuery = query<User>(connectionFactory) {
    from(User::class)
    select { all() }
    where {
        User::age gt 18
    }
}

println("SQL: ${sqlQuery.toSql()}")
// Output: SELECT * FROM user WHERE age > ?
```

### SELECT DISTINCT

```kotlin
val distinctEmails = query<User>(connectionFactory) {
    from(User::class)
    select {
        distinct()
        +User::email
    }
}.execute()
```

### Joins Complexos

```kotlin
val complexQuery = query<User>(connectionFactory) {
    from(User::class)
    select {
        +User::name
        +User::email
    }
    leftJoin {
        table(City::class)
        on(User::cityId, City::id)
    }
    innerJoin {
        table(Order::class)
        on(User::id, Order::userId)
    }
    where {
        City::country eq "Brazil"
        Order::status eq "active"
    }
    orderBy {
        User::name.asc()
    }
    limit(50)
}.execute()
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

## Roadmap

- [ ] Suporte a R2DBC para operações reativas
- [ ] Connection pooling integrado
- [ ] Suporte a migrations
- [ ] Geração automática de schema
- [ ] Cache de queries
- [ ] Suporte a mais bancos (SQLite, SQL Server, Oracle)

## Licença

MIT License

## Contribuindo

Contribuições são bem-vindas! Sinta-se à vontade para abrir issues ou pull requests.
