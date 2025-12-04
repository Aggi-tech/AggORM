# AggORM Spring Boot Starter

Integração oficial do AggORM com Spring Boot, fornecendo compatibilidade total com JPA e Spring Data.

## Features

✅ **AutoConfiguration** - Configuração automática via Spring Boot
✅ **JPA Compatible** - Suporta anotações JPA (`@Entity`, `@Id`, `@Column`, etc.)
✅ **Spring Data Repository** - Interface CrudRepository compatível
✅ **@Transactional Support** - Gerenciamento de transações do Spring
✅ **Type-Safe DSL** - Query builder com verificação em tempo de compilação
✅ **Standalone Core** - Core ORM 100% independente de frameworks

## Instalação

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.aggitech.orm:aggo-spring-boot-starter:1.0-SNAPSHOT")

    // Driver JDBC do seu banco
    runtimeOnly("org.postgresql:postgresql:42.7.1")
    // ou
    runtimeOnly("com.mysql:mysql-connector-j:8.2.0")
}
```

### Maven

```xml
<dependency>
    <groupId>com.aggitech.orm</groupId>
    <artifactId>aggo-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### 1. Configure application.yml

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
```

### 2. Crie sua Entity com JPA

```kotlin
import jakarta.persistence.*

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false, unique = true)
    val email: String,

    val age: Int
)
```

### 3. Crie o Repository

```kotlin
import com.aggitech.orm.spring.repository.SimpleAggoRepository
import org.springframework.stereotype.Repository

@Repository
class UserRepository(
    dbConfig: DbConfig,
    dialect: SqlDialect
) : SimpleAggoRepository<User, Long>(
    entityClass = User::class,
    dbConfig = dbConfig,
    dialect = dialect
)
```

### 4. Use no Service

```kotlin
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository
) {

    @Transactional(readOnly = true)
    fun findById(id: Long): User? {
        return userRepository.findById(id).orElse(null)
    }

    @Transactional
    fun save(user: User): User {
        return userRepository.save(user)
    }

    // Use DSL type-safe do AggORM
    @Transactional(readOnly = true)
    fun findAdults(): List<User> {
        return userRepository.findWhere {
            User::age gte 18
        }
    }
}
```

## API Reference

### CrudRepository Methods

```kotlin
interface AggoRepository<T, ID> : CrudRepository<T, ID> {
    // Métodos padrão do Spring Data
    save(entity: T): T
    saveAll(entities: Iterable<T>): Iterable<T>
    findById(id: ID): Optional<T>
    existsById(id: ID): Boolean
    findAll(): List<T>
    findAllById(ids: Iterable<ID>): List<T>
    count(): Long
    deleteById(id: ID)
    delete(entity: T)
    deleteAllById(ids: Iterable<ID>)
    deleteAll(entities: Iterable<T>)
    deleteAll()

    // Métodos com DSL AggORM
    findWhere(block: WhereBuilder<T>.() -> Predicate): List<T>
    findOneWhere(block: WhereBuilder<T>.() -> Predicate): T?
    countWhere(block: WhereBuilder<T>.() -> Predicate): Long
    deleteWhere(block: WhereBuilder<T>.() -> Predicate): Int
}
```

### DSL Examples

```kotlin
// Buscar com condições
userRepository.findWhere {
    (User::age gte 18) and (User::email like "%@gmail.com")
}

// Buscar um único registro
userRepository.findOneWhere {
    User::email eq "john@example.com"
}

// Contar com condição
userRepository.countWhere {
    User::age.between(18, 65)
}

// Deletar com condição
userRepository.deleteWhere {
    User::active eq false
}
```

## Supported JPA Annotations

### Entity Annotations
- `@Entity` - Marca uma classe como entidade
- `@Table` - Define nome e schema da tabela

### Field Annotations
- `@Id` - Marca campo como chave primária
- `@GeneratedValue` - Estratégia de geração de ID
- `@Column` - Metadados da coluna
- `@Transient` - Campo não persistente
- `@Temporal` - Tipo temporal (Date/Time)
- `@Enumerated` - Como persistir enums
- `@Lob` - Large Objects (BLOB/CLOB)
- `@Version` - Controle de concorrência otimista

### Relationship Annotations
- `@ManyToOne` - Relacionamento N:1
- `@OneToMany` - Relacionamento 1:N
- `@OneToOne` - Relacionamento 1:1
- `@ManyToMany` - Relacionamento N:N
- `@JoinColumn` - Coluna de join
- `@JoinTable` - Tabela de join

## Transaction Support

### @Transactional

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository
) {

    @Transactional
    fun createOrder(order: Order) {
        orderRepository.save(order)
        // Rollback automático em caso de exceção
    }

    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        propagation = Propagation.REQUIRES_NEW,
        rollbackFor = [BusinessException::class]
    )
    fun criticalOperation() {
        // Configuração avançada de transação
    }

    @Transactional(readOnly = true)
    fun searchOrders(): List<Order> {
        // Otimização para leitura
        return orderRepository.findAll()
    }
}
```

### Supported Isolation Levels

- `READ_UNCOMMITTED`
- `READ_COMMITTED`
- `REPEATABLE_READ`
- `SERIALIZABLE`

## Configuration Options

### Via application.yml

```yaml
aggo:
  orm:
    # Database connection
    database: mydb              # Required
    host: localhost             # Default: localhost
    port: 5432                  # Default: 5432 (PostgreSQL)
    username: postgres          # Required
    password: secret            # Required

    # Database type and dialect
    database-type: POSTGRESQL   # POSTGRESQL or MYSQL
    dialect: POSTGRESQL         # POSTGRESQL or MYSQL

    # Optional
    enabled: true               # Default: true
```

### Via application.properties

```properties
aggo.orm.database=mydb
aggo.orm.host=localhost
aggo.orm.port=5432
aggo.orm.username=postgres
aggo.orm.password=secret
aggo.orm.database-type=POSTGRESQL
aggo.orm.dialect=POSTGRESQL
```

### Programmatic Configuration

```kotlin
@Configuration
class AggoOrmConfig {

    @Bean
    fun aggoDbConfig(): DbConfig {
        return DbConfig(
            database = "mydb",
            host = "localhost",
            port = 5432,
            user = "postgres",
            password = "secret",
            type = SupportedDatabases.POSTGRESQL
        )
    }

    @Bean
    fun aggoSqlDialect(): SqlDialect {
        return PostgresDialect
    }
}
```

## Integration with Existing JPA

O AggORM pode conviver lado a lado com Hibernate/JPA:

```kotlin
@Service
class UserService(
    private val jpaUserRepository: JpaUserRepository,      // Hibernate JPA
    private val aggoUserRepository: AggoUserRepository     // AggORM
) {

    fun complexOperation() {
        // Use JPA para operações complexas com lazy loading
        val user = jpaUserRepository.findByIdWithPosts(userId)

        // Use AggORM para queries type-safe simples
        val adults = aggoUserRepository.findWhere {
            User::age gte 18
        }
    }
}
```

## Testing

### Unit Tests

```kotlin
class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var userService: UserService

    @BeforeEach
    fun setup() {
        val dbConfig = DbConfig(/* test config */)
        userRepository = UserRepository(dbConfig, PostgresDialect)
        userService = UserService(userRepository)
    }

    @Test
    fun `should find adults`() {
        // Test usando repository real ou mock
    }
}
```

### Integration Tests

```kotlin
@SpringBootTest
class UserRepositoryIntegrationTest {

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    @Transactional
    fun `should save and find user`() {
        val user = User(name = "John", email = "john@test.com", age = 30)
        val saved = userRepository.save(user)

        assertThat(saved.id).isNotNull()

        val found = userRepository.findById(saved.id!!).get()
        assertThat(found.name).isEqualTo("John")
    }
}
```

## Migration from JPA

### Step 1: Add Dependency

```kotlin
implementation("com.aggitech.orm:aggo-spring-boot-starter:1.0-SNAPSHOT")
```

### Step 2: Configure Properties

```yaml
aggo:
  orm:
    database: ${spring.datasource.database}
    host: ${DB_HOST:localhost}
    port: ${DB_PORT:5432}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:secret}
    database-type: POSTGRESQL
```

### Step 3: Create AggORM Repositories

```kotlin
@Repository
class UserAggoRepository(dbConfig: DbConfig, dialect: SqlDialect)
    : SimpleAggoRepository<User, Long>(User::class, dbConfig, dialect)
```

### Step 4: Gradual Migration

Mantenha ambos e migre gradualmente:

```kotlin
@Service
class UserService(
    private val jpaRepository: JpaUserRepository,
    private val aggoRepository: UserAggoRepository
) {
    // Migre método por método conforme necessário
}
```

## Troubleshooting

### "No qualifying bean of type 'DbConfig'"

**Solução**: Verifique se as properties estão configuradas corretamente.

### "Transaction not active"

**Solução**: Adicione `@Transactional` no método ou classe de serviço.

### "Invalid Gradle JDK configuration"

**Solução**: Configure JDK 21+:
```kotlin
kotlin {
    jvmToolchain(21)
}
```

## Examples

Veja exemplos completos em:
- [SPRING_BOOT_INTEGRATION.md](../SPRING_BOOT_INTEGRATION.md) - Guia completo de integração
- [MODULE_ARCHITECTURE.md](../MODULE_ARCHITECTURE.md) - Arquitetura modular

## License

MIT License - veja LICENSE para detalhes.
