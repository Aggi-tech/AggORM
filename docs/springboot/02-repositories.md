# Spring Boot - Repositories

O AggORM fornece integração com o padrão Repository do Spring Data, oferecendo uma interface familiar com a potência da DSL type-safe.

## AggoRepository

Interface base que estende `CrudRepository` do Spring Data:

```kotlin
interface AggoRepository<T : Any, ID : Any> : CrudRepository<T, ID> {

    // Métodos padrão do CrudRepository
    override fun save(entity: T): T
    override fun saveAll(entities: Iterable<T>): Iterable<T>
    override fun findById(id: ID): Optional<T>
    override fun existsById(id: ID): Boolean
    override fun findAll(): Iterable<T>
    override fun findAllById(ids: Iterable<ID>): Iterable<T>
    override fun count(): Long
    override fun deleteById(id: ID)
    override fun delete(entity: T)
    override fun deleteAllById(ids: Iterable<ID>)
    override fun deleteAll(entities: Iterable<T>)
    override fun deleteAll()

    // Métodos adicionais do AggORM
    fun findWhere(block: WhereBuilder<T>.() -> Predicate): List<T>
    fun findOneWhere(block: WhereBuilder<T>.() -> Predicate): T?
    fun countWhere(block: WhereBuilder<T>.() -> Predicate): Long
    fun deleteWhere(block: WhereBuilder<T>.() -> Predicate): Long
}
```

## Criando um Repository

### Definir Entidade

```kotlin
import jakarta.persistence.Id

data class User(
    @Id
    val id: Long = 0,
    val name: String,
    val email: String,
    val age: Int,
    val active: Boolean = true
)
```

### Criar Interface do Repository

```kotlin
import com.aggitech.orm.spring.repository.AggoRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : AggoRepository<User, Long>
```

### Injetar e Usar

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository
) {

    fun createUser(name: String, email: String, age: Int): User {
        val user = User(name = name, email = email, age = age)
        return userRepository.save(user)
    }

    fun findActiveUsers(): List<User> {
        return userRepository.findWhere {
            User::active eq true
        }
    }

    fun findByEmail(email: String): User? {
        return userRepository.findOneWhere {
            User::email eq email
        }
    }
}
```

## Métodos CRUD Padrão

### save()

Salva uma entidade (insert ou update):

```kotlin
// Insert (ID = 0 ou null)
val newUser = User(name = "John", email = "john@example.com", age = 30)
val saved = userRepository.save(newUser)
println("ID gerado: ${saved.id}")

// Update (ID existente)
val existing = userRepository.findById(1L).orElseThrow()
val updated = existing.copy(name = "John Updated")
userRepository.save(updated)
```

### saveAll()

Salva múltiplas entidades:

```kotlin
val users = listOf(
    User(name = "John", email = "john@example.com", age = 30),
    User(name = "Jane", email = "jane@example.com", age = 25),
    User(name = "Bob", email = "bob@example.com", age = 35)
)

val saved = userRepository.saveAll(users)
```

### findById()

Busca por ID:

```kotlin
val user = userRepository.findById(1L)

if (user.isPresent) {
    println("Usuário: ${user.get().name}")
} else {
    println("Usuário não encontrado")
}

// Ou usando Kotlin extensions
val user = userRepository.findById(1L).orElseThrow {
    NotFoundException("Usuário não encontrado")
}
```

### findAll()

Busca todos os registros:

```kotlin
val allUsers = userRepository.findAll()

allUsers.forEach { user ->
    println("${user.id}: ${user.name}")
}
```

### findAllById()

Busca múltiplos por IDs:

```kotlin
val users = userRepository.findAllById(listOf(1L, 2L, 3L))
```

### existsById()

Verifica se existe:

```kotlin
if (userRepository.existsById(1L)) {
    println("Usuário existe")
}
```

### count()

Conta registros:

```kotlin
val total = userRepository.count()
println("Total de usuários: $total")
```

### deleteById()

Remove por ID:

```kotlin
userRepository.deleteById(1L)
```

### delete()

Remove entidade:

```kotlin
val user = userRepository.findById(1L).orElseThrow()
userRepository.delete(user)
```

### deleteAll()

Remove todos ou específicos:

```kotlin
// Remover todos
userRepository.deleteAll()

// Remover específicos
val usersToDelete = userRepository.findWhere { User::active eq false }
userRepository.deleteAll(usersToDelete)
```

## Métodos DSL do AggORM

### findWhere()

Busca com condições usando DSL:

```kotlin
// Condição simples
val activeUsers = userRepository.findWhere {
    User::active eq true
}

// Múltiplas condições
val adultActiveUsers = userRepository.findWhere {
    (User::active eq true) and (User::age gte 18)
}

// Condições complexas
val searchResults = userRepository.findWhere {
    ((User::name like "%John%") or (User::email like "%john%")) and
    (User::active eq true) and
    (User::age.between(18, 65))
}
```

### findOneWhere()

Busca um único resultado:

```kotlin
val user = userRepository.findOneWhere {
    User::email eq "john@example.com"
}

if (user != null) {
    println("Encontrado: ${user.name}")
}
```

### countWhere()

Conta com condições:

```kotlin
val activeCount = userRepository.countWhere {
    User::active eq true
}

val adultCount = userRepository.countWhere {
    User::age gte 18
}
```

### deleteWhere()

Remove com condições:

```kotlin
// Remove usuários inativos
val deleted = userRepository.deleteWhere {
    User::active eq false
}
println("Removidos: $deleted usuários")

// Remove por condição complexa
val deleted = userRepository.deleteWhere {
    (User::active eq false) and (User::age lt 18)
}
```

## Implementação Customizada

### SimpleAggoRepository

Implemente `SimpleAggoRepository` para lógica customizada:

```kotlin
@Repository
class UserRepositoryImpl : SimpleAggoRepository<User, Long>(User::class) {

    fun findByEmailDomain(domain: String): List<User> {
        return findWhere {
            User::email like "%@$domain"
        }
    }

    fun findRecentUsers(since: Timestamp): List<User> {
        return select<User> {
            where { User::createdAt gte since }
            orderBy(User::createdAt, OrderDirection.DESC)
        }.execute()
    }

    fun updateLastLogin(userId: Long, timestamp: Timestamp) {
        update<User> {
            set(User::lastLoginAt, timestamp)
            where { User::id eq userId }
        }.execute()
    }
}
```

### Métodos de Query Customizados

```kotlin
interface UserRepository : AggoRepository<User, Long> {

    // Métodos customizados são implementados na classe
}

@Repository
class UserRepositoryImpl(
    private val dbConfig: DbConfig
) : SimpleAggoRepository<User, Long>(User::class), UserRepository {

    fun searchUsers(
        name: String? = null,
        email: String? = null,
        minAge: Int? = null,
        maxAge: Int? = null,
        active: Boolean? = null
    ): List<User> {
        return select<User> {
            where {
                var predicate: Predicate? = null

                name?.let {
                    predicate = addPredicate(predicate, User::name like "%$it%")
                }

                email?.let {
                    predicate = addPredicate(predicate, User::email like "%$it%")
                }

                minAge?.let {
                    predicate = addPredicate(predicate, User::age gte it)
                }

                maxAge?.let {
                    predicate = addPredicate(predicate, User::age lte it)
                }

                active?.let {
                    predicate = addPredicate(predicate, User::active eq it)
                }

                predicate ?: (User::id gt 0L)  // Fallback para buscar todos
            }
        }.execute()
    }

    private fun addPredicate(existing: Predicate?, new: Predicate): Predicate {
        return if (existing != null) existing and new else new
    }
}
```

## Paginação

### Implementação Manual

```kotlin
@Repository
class UserRepositoryImpl : SimpleAggoRepository<User, Long>(User::class) {

    fun findAllPaged(page: Int, size: Int): PagedResult<User> {
        val total = count()

        val items = select<User> {
            orderBy(User::id, OrderDirection.ASC)
            limit(size)
            offset(page * size)
        }.execute()

        return PagedResult(
            items = items,
            page = page,
            size = size,
            totalItems = total,
            totalPages = (total + size - 1) / size
        )
    }
}

data class PagedResult<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Long
)
```

## Uso em Controllers

### REST Controller

```kotlin
@RestController
@RequestMapping("/api/users")
class UserController(
    private val userRepository: UserRepository
) {

    @GetMapping
    fun listUsers(): List<User> {
        return userRepository.findAll().toList()
    }

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): ResponseEntity<User> {
        return userRepository.findById(id)
            .map { ResponseEntity.ok(it) }
            .orElse(ResponseEntity.notFound().build())
    }

    @PostMapping
    fun createUser(@RequestBody user: User): User {
        return userRepository.save(user)
    }

    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: Long,
        @RequestBody updates: User
    ): ResponseEntity<User> {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }

        val updated = updates.copy(id = id)
        return ResponseEntity.ok(userRepository.save(updated))
    }

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }

        userRepository.deleteById(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/search")
    fun searchUsers(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) active: Boolean?
    ): List<User> {
        return userRepository.findWhere {
            var predicate: Predicate = User::id gt 0L

            name?.let {
                predicate = predicate and (User::name like "%$it%")
            }

            active?.let {
                predicate = predicate and (User::active eq it)
            }

            predicate
        }
    }
}
```

## Annotation @Id

### Marcando Primary Key

Use `@Id` do Jakarta Persistence para marcar a primary key:

```kotlin
import jakarta.persistence.Id

data class User(
    @Id
    val id: Long = 0,
    val name: String,
    val email: String
)

data class Post(
    @Id
    val id: Long = 0,
    val title: String,
    val content: String
)
```

### ID Composto

Para IDs compostos, use classe separada:

```kotlin
data class OrderItemId(
    val orderId: Long,
    val productId: Long
)

data class OrderItem(
    @Id
    val id: OrderItemId,
    val quantity: Int,
    val price: BigDecimal
)
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Usar Optional corretamente
val user = userRepository.findById(id).orElseThrow {
    NotFoundException("User $id not found")
}

// Validar antes de salvar
fun createUser(user: User): User {
    user.validateAndThrow()
    return userRepository.save(user)
}

// Usar findWhere para consultas complexas
val results = userRepository.findWhere {
    (User::active eq true) and (User::age gte 18)
}
```

### [AVOID] Evitar

```kotlin
// Não ignorar Optional
val user = userRepository.findById(id).get()  // [AVOID] Pode lançar NoSuchElementException

// Não fazer N+1 queries
users.forEach { user ->
    val posts = postRepository.findByUserId(user.id)  // [AVOID] N queries
}

// Não usar findAll para grandes datasets
val allUsers = userRepository.findAll()  // [AVOID] Pode causar OutOfMemory
```

## Próximos Passos

- [Transactions](./03-transactions.md) - Gerenciamento de transações
- [Migrations](./04-migrations.md) - Migrations automáticas
