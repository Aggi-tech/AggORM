# AggORM - Kotlin ORM Declarativo

**AggORM** é um ORM leve, type-safe e declarativo para Kotlin que combina a performance do JDBC com a elegância de uma DSL expressiva.

## Características Principais

- **DSL Declarativa**: Sintaxe expressiva e legível inspirada em query builders modernos
- **Type-Safe**: Totalmente tipado usando Kotlin Reflection e Property References
- **JDBC**: Baseado em JDBC para máxima compatibilidade e performance
- **SQL Injection Protection**: Prepared Statements automáticos em todas as operações
- **Validação de Entidades**: Sistema de validação integrado com annotations
- **Funções de Agregação**: Suporte a COUNT, SUM, AVG, MIN, MAX
- **Spring Boot Integration**: Starter para integração fácil com Spring Boot

## Instalação

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.aggitech.orm:aggo-core:1.0-SNAPSHOT")
    implementation(kotlin("reflect"))

    // Driver JDBC (escolha o seu banco)
    implementation("org.postgresql:postgresql:42.7.1") // PostgreSQL
    // ou
    implementation("com.mysql:mysql-connector-j:8.0.33") // MySQL
}

// Para Spring Boot
dependencies {
    implementation("com.aggitech.orm:aggo-spring-boot-starter:1.0-SNAPSHOT")
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
val query = select<User> {
    select {
        +User::name
        +User::email
        +User::age
    }
    where {
        User::age gte 18
    }
    orderBy {
        User::name.asc()
    }
    limit(10)
}

// Renderizar SQL
val renderer = SelectRenderer(PostgresDialect)
val rendered = renderer.render(query)
println("SQL: ${rendered.sql}")
println("Parameters: ${rendered.parameters}")

// Query com WHERE complexo
val adults = select<User> {
    where {
        ((User::age gte 18) and (User::age lte 65)) and
            (User::email like "%@gmail.com")
    }
    orderBy {
        User::age.desc()
    }
}

// Paginação
val paged = select<User> {
    limit(10)
    offset(20)
}
```

### Funções de Agregação

```kotlin
// COUNT, SUM, AVG, MIN, MAX
val aggregateQuery = select<User> {
    select {
        countAll("total_users")
        avg(User::age, "average_age")
        min(User::age, "min_age")
        max(User::age, "max_age")
    }
}

// GROUP BY e HAVING
val groupByQuery = select<Order> {
    select {
        +Order::userId
        sum(Order::totalAmount, "total_spent")
        countAll("order_count")
    }
    groupBy(Order::userId)
    having {
        Order::totalAmount gt 1000.0
    }
    orderBy {
        Order::totalAmount.desc()
    }
}
```

### Operadores WHERE Suportados

```kotlin
where {
    User::age eq 18                      // =
    User::age ne 18                      // !=
    User::age gt 18                      // >
    User::age gte 18                     // >=
    User::age lt 18                      // <
    User::age lte 18                     // <=
    User::email like "%@gmail.com"       // LIKE
    User::email notLike "%@spam.com"     // NOT LIKE
    User::id inList listOf(1, 2, 3)      // IN
    User::id notInList listOf(4, 5)      // NOT IN
    User::email.isNull()                 // IS NULL
    User::email.isNotNull()              // IS NOT NULL
    User::age.between(18, 65)            // BETWEEN

    // Operadores lógicos
    (User::age gte 18) and (User::age lte 65)
    (User::cityId eq 1L) or (User::cityId eq 2L)
    not(User::email like "%@spam.com")
}
```

### INSERT Operations

```kotlin
// INSERT com valores explícitos
val insertQuery = insert<User> {
    User::name to "John Doe"
    User::email to "john@example.com"
    User::age to 30
    User::cityId to 1L
}

val renderer = InsertRenderer(PostgresDialect)
val rendered = renderer.render(insertQuery)

// INSERT usando uma entidade
val user = User(
    name = "Jane Smith",
    email = "jane@example.com",
    age = 28,
    cityId = 2L
)
val entityInsert = insert(user)
```

### UPDATE Operations

```kotlin
val updateQuery = update<User> {
    User::name to "John Updated"
    User::age to 31
    where {
        User::id eq 1L
    }
}

val renderer = UpdateRenderer(PostgresDialect)
val rendered = renderer.render(updateQuery)

// Update condicional
val conditionalUpdate = update<User> {
    User::cityId to null
    where {
        (User::age lt 18) or (User::age gt 100)
    }
}
```

### DELETE Operations

```kotlin
val deleteQuery = delete<User> {
    where {
        User::id eq 999L
    }
}

val renderer = DeleteRenderer(PostgresDialect)
val rendered = renderer.render(deleteQuery)

// Delete condicional
val conditionalDelete = delete<User> {
    where {
        (User::age lt 18) and (User::email like "%@temporary.com")
    }
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

## Validação de Entidades

```kotlin
// Usando o sistema de validação
val validUser = User(
    id = 1L,
    name = "Alice Johnson",
    email = "alice@example.com",
    age = 25,
    cityId = 1L
)

val validResult = validUser.validate()
println("Valid user: ${validResult.isValid}")

val invalidUser = User(
    id = 2L,
    name = "B",                    // Muito curto
    email = "invalid-email",       // Email inválido
    age = 15,                      // Menor de idade
    cityId = 1L
)

val invalidResult = invalidUser.validate()
println("Invalid user: ${invalidResult.isValid}")
invalidResult.errors.forEach { error ->
    println("  ${error.property}: ${error.message}")
}
```

## Resolução de Nomes (Snake Case)

```kotlin
// O AggORM converte automaticamente nomes de classes e propriedades para snake_case
println("User -> ${EntityRegistry.resolveTable(User::class)}")         // "user"
println("City -> ${EntityRegistry.resolveTable(City::class)}")         // "city"
println("User::cityId -> ${EntityRegistry.resolveColumn(User::cityId)}") // "city_id"
println("User::name -> ${EntityRegistry.resolveColumn(User::name)}")     // "name"
```

## SELECT DISTINCT

```kotlin
val distinctQuery = select<User> {
    select {
        distinct()
        +User::email
    }
}
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
