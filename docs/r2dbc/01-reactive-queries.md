# Reactive Queries - Queries Reativas com R2DBC

O AggORM fornece suporte completo a programação reativa usando R2DBC com Kotlin Coroutines.

## Visão Geral

O módulo R2DBC permite executar queries de forma não-bloqueante usando:
- **Kotlin Coroutines**: Funções `suspend` para operações assíncronas
- **Flow**: Streaming reativo de grandes volumes de dados
- **R2DBC**: Driver reativo para bancos de dados relacionais

## R2dbcQueryExecutor

Classe principal para executar queries reativas.

### Características

- Operações suspend (não-bloqueantes)
- Suporte a Flow para streaming
- Integração automática com `R2dbcConnectionManager`
- Mapeamento automático de entidades
- Tratamento de erros reativo

## Queries SELECT

### executeQuery() - Suspend Function

Executa uma query SELECT e retorna todos os resultados:

```kotlin
import com.aggitech.orm.r2dbc.executeQuery
import kotlinx.coroutines.runBlocking

data class User(
    val id: Long,
    val name: String,
    val email: String
)

// Query básica
suspend fun getActiveUsers(): List<User> {
    return select<User> {
        where { User::active eq true }
    }.executeQuery()
}

// Uso com coroutines
runBlocking {
    val users = getActiveUsers()
    users.forEach { println(it) }
}
```

### executeQueryAsFlow() - Streaming

Executa query e retorna Flow para streaming de dados:

```kotlin
import com.aggitech.orm.r2dbc.executeQueryAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

// Streaming de grandes volumes
suspend fun streamAllUsers(): Flow<Map<String, Any?>> {
    return select<User> {
        orderBy(User::id, OrderDirection.ASC)
    }.executeQueryAsFlow()
}

// Processar dados em streaming
runBlocking {
    streamAllUsers().collect { user ->
        // Processa cada usuário sem carregar tudo na memória
        println("Processing user: $user")
    }
}
```

### Mapeamento Automático

```kotlin
import com.aggitech.orm.r2dbc.executeQuery

// Retorna List<User> com mapeamento automático
suspend fun getUsersByAge(minAge: Int): List<User> {
    val results = select<User> {
        where { User::age gte minAge }
    }.executeQuery()

    // Mapear para entidades
    return results.map { it.toEntity<User>() }
}
```

## Queries INSERT

### executeInsert() - Inserir Entidade

```kotlin
import com.aggitech.orm.r2dbc.executeInsert

suspend fun createUser(name: String, email: String) {
    val user = User(id = 0, name = name, email = email)

    insert(user).executeInsert()
}

// Uso
runBlocking {
    createUser("John Doe", "john@example.com")
}
```

### executeInsertReturningKeys() - Com Chaves Geradas

```kotlin
import com.aggitech.orm.r2dbc.executeInsertReturningKeys

suspend fun createUserAndGetId(name: String, email: String): Long {
    val user = User(id = 0, name = name, email = email)

    val generatedKeys = insert(user).executeInsertReturningKeys()

    return generatedKeys["id"] as Long
}

// Uso
runBlocking {
    val userId = createUserAndGetId("Jane Doe", "jane@example.com")
    println("Created user with ID: $userId")
}
```

## Queries UPDATE

### executeUpdate() - Atualizar Registros

```kotlin
import com.aggitech.orm.r2dbc.executeUpdate

suspend fun updateUserEmail(userId: Long, newEmail: String): Long {
    return update<User> {
        set(User::email, newEmail)
        where { User::id eq userId }
    }.executeUpdate()
}

// Uso
runBlocking {
    val updatedRows = updateUserEmail(1L, "newemail@example.com")
    println("Updated $updatedRows rows")
}
```

## Queries DELETE

### executeDelete() - Deletar Registros

```kotlin
import com.aggitech.orm.r2dbc.executeDelete

suspend fun deleteInactiveUsers(): Long {
    return delete<User> {
        where { User::active eq false }
    }.executeDelete()
}

// Uso
runBlocking {
    val deletedCount = deleteInactiveUsers()
    println("Deleted $deletedCount inactive users")
}
```

## Configurações Nomeadas

### Usar Diferentes Conexões R2DBC

```kotlin
// Query no banco primário
suspend fun getPrimaryUsers() = select<User> {
    where { User::active eq true }
}.executeQuery()

// Query no banco de analytics
suspend fun getAnalyticsEvents() = select<AnalyticsEvent> {
    where { AnalyticsEvent::timestamp gte today }
}.executeQuery(configName = "analytics")
```

## Processamento em Paralelo

### Executar Múltiplas Queries Simultaneamente

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

suspend fun loadDashboardData(): DashboardData {
    coroutineScope {
        // Executar queries em paralelo
        val usersDeferred = async { getActiveUsers() }
        val ordersDeferred = async { getRecentOrders() }
        val statsDeferred = async { getStatistics() }

        // Aguardar todos os resultados
        val (users, orders, stats) = awaitAll(
            usersDeferred,
            ordersDeferred,
            statsDeferred
        )

        return DashboardData(
            users = users,
            orders = orders,
            statistics = stats
        )
    }
}
```

## Flow - Streaming Avançado

### Transformação de Dados em Streaming

```kotlin
import kotlinx.coroutines.flow.*

suspend fun processUsersInBatches() {
    select<User> {
        orderBy(User::id, OrderDirection.ASC)
    }
    .executeQueryAsFlow()
    .map { it.toEntity<User>() }
    .filter { it.age >= 18 }
    .chunked(100)  // Processar em lotes de 100
    .collect { batch ->
        // Processar cada lote
        processBatch(batch)
    }
}
```

### Backpressure com Flow

```kotlin
import kotlinx.coroutines.flow.buffer

suspend fun streamLargeDataset() {
    select<LargeEntity> {
        // Query muito grande
    }
    .executeQueryAsFlow()
    .buffer(capacity = 50)  // Buffer para controle de backpressure
    .collect { entity ->
        // Processar entidade
        processEntity(entity)
    }
}
```

### Combinar Múltiplos Flows

```kotlin
import kotlinx.coroutines.flow.zip

suspend fun combineUserDataStreams() {
    val usersFlow = select<User> {}.executeQueryAsFlow()
    val profilesFlow = select<Profile> {}.executeQueryAsFlow()

    usersFlow.zip(profilesFlow) { user, profile ->
        UserWithProfile(user, profile)
    }.collect { combined ->
        println(combined)
    }
}
```

## Error Handling

### Try-Catch em Suspend Functions

```kotlin
suspend fun safeGetUsers(): List<User> {
    return try {
        select<User> {
            where { User::active eq true }
        }.executeQuery().map { it.toEntity<User>() }
    } catch (e: R2dbcException) {
        logger.error("Failed to fetch users", e)
        emptyList()
    }
}
```

### Flow Error Handling

```kotlin
import kotlinx.coroutines.flow.catch

suspend fun streamUsersWithErrorHandling() {
    select<User> {}
        .executeQueryAsFlow()
        .catch { e ->
            logger.error("Error during streaming", e)
            emit(emptyMap())  // Valor padrão em caso de erro
        }
        .collect { user ->
            processUser(user)
        }
}
```

## Integração com Coroutine Contexts

### Dispatcher Customizado

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun getUsers() = withContext(Dispatchers.IO) {
    select<User> {
        where { User::active eq true }
    }.executeQuery()
}
```

### Timeout

```kotlin
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

suspend fun getUsersWithTimeout(): List<User> {
    return withTimeout(5.seconds) {
        select<User> {
            where { User::active eq true }
        }.executeQuery().map { it.toEntity<User>() }
    }
}
```

## Exemplo Completo - API Reativa

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserRepository {

    suspend fun findById(id: Long): User? {
        val results = select<User> {
            where { User::id eq id }
        }.executeQuery()

        return results.firstOrNull()?.toEntity()
    }

    suspend fun findAll(): List<User> {
        return select<User> {
            orderBy(User::name, OrderDirection.ASC)
        }.executeQuery().map { it.toEntity<User>() }
    }

    fun streamAll(): Flow<User> {
        return select<User> {
            orderBy(User::id, OrderDirection.ASC)
        }
        .executeQueryAsFlow()
        .map { it.toEntity<User>() }
    }

    suspend fun create(user: User): Long {
        val keys = insert(user).executeInsertReturningKeys()
        return keys["id"] as Long
    }

    suspend fun update(user: User): Long {
        return update<User> {
            set(User::name, user.name)
            set(User::email, user.email)
            where { User::id eq user.id }
        }.executeUpdate()
    }

    suspend fun delete(id: Long): Long {
        return delete<User> {
            where { User::id eq id }
        }.executeDelete()
    }
}

// Uso
suspend fun main() {
    val repo = UserRepository()

    // Criar usuário
    val userId = repo.create(User(0, "John", "john@example.com"))

    // Buscar por ID
    val user = repo.findById(userId)

    // Listar todos
    val allUsers = repo.findAll()

    // Streaming
    repo.streamAll().collect { user ->
        println(user)
    }
}
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Usar Flow para grandes volumes de dados
fun streamLargeDataset(): Flow<User> {
    return select<User> {}
        .executeQueryAsFlow()
        .map { it.toEntity<User>() }
}

// Executar queries independentes em paralelo
suspend fun loadData() = coroutineScope {
    val users = async { getUsers() }
    val orders = async { getOrders() }
    Pair(users.await(), orders.await())
}

// Tratar erros apropriadamente
suspend fun safeQuery() = try {
    executeQuery()
} catch (e: Exception) {
    logger.error("Query failed", e)
    emptyList()
}
```

### [AVOID] Evitar

```kotlin
// Não bloquear thread com runBlocking em código de produção
fun getUsers(): List<User> {
    return runBlocking {  // [AVOID] Bloqueia thread
        select<User> {}.executeQuery()
    }
}

// Não carregar datasets grandes de uma vez
suspend fun getAllData() {
    val allData = select<LargeTable> {}
        .executeQuery()  // [AVOID] Pode causar OutOfMemoryError
}

// Não ignorar erros
suspend fun queryWithoutErrorHandling() {
    select<User> {}.executeQuery()  // [AVOID] Sem tratamento de erro
}
```

## Comparação: Blocking vs Non-Blocking

### Blocking (JDBC)

```kotlin
// Bloqueia a thread até completar
fun getUsers(): List<User> {
    return select<User> {
        where { User::active eq true }
    }.execute()  // Bloqueia
}
```

### Non-Blocking (R2DBC)

```kotlin
// Não bloqueia - suspende coroutine
suspend fun getUsers(): List<User> {
    return select<User> {
        where { User::active eq true }
    }.executeQuery()  // Suspende
}
```

## Performance

### Comparação de Throughput

```kotlin
// JDBC - Execução sequencial bloqueante
fun processUsersBlocking() {
    val users = getUsersJdbc()  // Bloqueia
    users.forEach { processUser(it) }  // Processa cada um
}

// R2DBC - Execução concorrente não-bloqueante
suspend fun processUsersReactive() {
    streamUsersR2dbc()  // Não bloqueia
        .collect { processUser(it) }  // Processa em streaming
}
```
