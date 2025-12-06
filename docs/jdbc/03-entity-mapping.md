# Entity Mapping - Mapeamento de Entidades

O AggORM fornece mapeamento automático entre resultados SQL (`Map<String, Any?>`) e classes Kotlin.

## EntityMapper

Classe responsável pelo mapeamento automático usando reflection do Kotlin.

### Características

- Mapeamento baseado no construtor primário
- Conversão automática de tipos
- Conversão de caso (snake_case ↔ camelCase)
- Suporte a propriedades nullable
- Type-safe com KClass e KProperty

## Uso Básico

### Mapeamento Simples

```kotlin
data class User(
    val id: Long,
    val name: String,
    val email: String
)

// Resultado do banco (snake_case)
val resultMap = mapOf(
    "id" to 1L,
    "name" to "John Doe",
    "email" to "john@example.com"
)

// Mapear para entidade
val user = EntityMapper.map<User>(resultMap)
// User(id=1, name="John Doe", email="john@example.com")
```

### Mapeamento com Conversão de Caso

```kotlin
data class UserProfile(
    val userId: Long,
    val firstName: String,
    val lastName: String,
    val createdAt: Timestamp
)

// Resultado do banco em snake_case
val dbResult = mapOf(
    "user_id" to 1L,
    "first_name" to "John",
    "last_name" to "Doe",
    "created_at" to Timestamp.valueOf("2024-01-01 10:00:00")
)

// Mapeamento automático com conversão
val profile = EntityMapper.map<UserProfile>(dbResult)
// UserProfile(userId=1, firstName="John", lastName="Doe", ...)
```

## Extension Functions

### toEntity()

Converte um único Map para entidade:

```kotlin
val userMap = mapOf("id" to 1L, "name" to "John")
val user = userMap.toEntity<User>()
```

### toEntities()

Converte uma lista de Maps para lista de entidades:

```kotlin
val userMaps = listOf(
    mapOf("id" to 1L, "name" to "John"),
    mapOf("id" to 2L, "name" to "Jane")
)

val users: List<User> = userMaps.toEntities()
```

## Conversão Automática de Tipos

### Tipos Suportados

O EntityMapper converte automaticamente os seguintes tipos:

```kotlin
data class Example(
    val stringField: String,
    val intField: Int,
    val longField: Long,
    val doubleField: Double,
    val floatField: Float,
    val booleanField: Boolean,
    val timestampField: Timestamp,
    val dateField: Date
)
```

### Conversão de Strings

```kotlin
// String → Int
mapOf("age" to "25").toEntity<Person>()
// Person(age=25)

// String → Long
mapOf("id" to "12345").toEntity<Entity>()
// Entity(id=12345L)

// String → Boolean
mapOf("active" to "true").toEntity<User>()
// User(active=true)
```

### Tipos Customizados

Para tipos não padrão, o mapper faz cast direto:

```kotlin
enum class Role { ADMIN, USER }

data class User(
    val id: Long,
    val role: Role
)

// O enum já vem convertido do banco
val user = mapOf(
    "id" to 1L,
    "role" to Role.ADMIN
).toEntity<User>()
```

## Conversão de Caso

### Regras de Conversão

O EntityMapper aplica conversão snake_case ↔ camelCase automaticamente:

```kotlin
// Banco de dados (snake_case) → Kotlin (camelCase)
"user_id" → userId
"first_name" → firstName
"created_at" → createdAt
"is_active" → isActive
"https_connection" → httpsConnection
```

### Exemplos

```kotlin
data class AdvancedUser(
    val userId: Long,
    val firstName: String,
    val lastName: String,
    val emailAddress: String,
    val isActive: Boolean,
    val createdAt: Timestamp
)

val dbResult = mapOf(
    "user_id" to 1L,
    "first_name" to "John",
    "last_name" to "Doe",
    "email_address" to "john@example.com",
    "is_active" to true,
    "created_at" to Timestamp.valueOf("2024-01-01 10:00:00")
)

val user = dbResult.toEntity<AdvancedUser>()
// Todos os campos mapeados corretamente
```

## Propriedades Nullable

### Suporte Completo a Nullable

```kotlin
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val bio: String? = null,        // Nullable com default
    val avatarUrl: String? = null
)

// Valores null são tratados corretamente
val user = mapOf(
    "id" to 1L,
    "name" to "John",
    "email" to "john@example.com",
    "bio" to null,
    "avatar_url" to null
).toEntity<User>()
// User(id=1, name="John", email="john@example.com", bio=null, avatarUrl=null)
```

### Campos Ausentes

```kotlin
// Campos ausentes usam valores default
val user = mapOf(
    "id" to 1L,
    "name" to "John",
    "email" to "john@example.com"
    // bio e avatarUrl ausentes
).toEntity<User>()
// User(id=1, name="John", email="john@example.com", bio=null, avatarUrl=null)
```

## Integração com Queries

### Mapeamento Automático em Queries

```kotlin
// O método execute() já faz mapeamento automático
val users: List<User> = select<User> {
    where { User::active eq true }
}.execute()

// Cada Map<String, Any?> é convertido para User automaticamente
```

### Mapeamento Customizado

```kotlin
// Se precisar de controle manual
val results: List<Map<String, Any?>> = select<User> {
    select(User::id, User::name)
}.execute()

val userDtos = results.map { row ->
    UserDTO(
        id = row["id"] as Long,
        name = row["name"] as String
    )
}
```

## EntityRegistry

O EntityMapper usa o `EntityRegistry` para cachear metadados:

```kotlin
// Resolução de nomes de tabela
EntityRegistry.getTableName(User::class)
// "users"

EntityRegistry.getTableName(UserProfile::class)
// "user_profiles"

// Resolução de nomes de colunas
EntityRegistry.getColumnName(User::name)
// "name"

EntityRegistry.getColumnName(UserProfile::firstName)
// "first_name"
```

### Conversão CamelCase → snake_case

```kotlin
// Classes
UserProfile → user_profile
HTTPSConnection → https_connection
XMLParser → xml_parser

// Propriedades
firstName → first_name
userId → user_id
createdAt → created_at
isActive → is_active
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Usar data classes para entidades
data class User(val id: Long, val name: String)

// Propriedades nullable com defaults para campos opcionais
data class User(
    val id: Long,
    val name: String,
    val bio: String? = null
)

// Seguir convenções de nomenclatura
data class UserProfile(  // snake_case no banco: user_profile
    val userId: Long,    // snake_case no banco: user_id
    val firstName: String // snake_case no banco: first_name
)
```

### [AVOID] Evitar

```kotlin
// Evitar classes mutáveis
class User(
    var id: Long,
    var name: String
)

// Evitar nomes que não seguem convenções
data class user_profile(  // Use UserProfile
    val user_id: Long     // Use userId
)

// Evitar tipos complexos sem conversão customizada
data class User(
    val id: Long,
    val metadata: ComplexObject  // Pode falhar no mapeamento
)
```

## Limitações

### Tipos Não Suportados Automaticamente

Para tipos complexos, implemente conversão manual:

```kotlin
data class User(
    val id: Long,
    val name: String,
    val tags: List<String>  // Requer conversão manual
)

// Conversão manual para arrays/listas
val dbResult = mapOf(
    "id" to 1L,
    "name" to "John",
    "tags" to arrayOf("kotlin", "orm")
)

val user = User(
    id = dbResult["id"] as Long,
    name = dbResult["name"] as String,
    tags = (dbResult["tags"] as Array<*>).map { it.toString() }
)
```

### Construtor Primário Obrigatório

```kotlin
// [OK] Funciona - construtor primário
data class User(val id: Long, val name: String)

// [AVOID] Não funciona - sem construtor primário
class User {
    var id: Long = 0
    var name: String = ""
}
```

## Exemplo Completo

```kotlin
data class User(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val email: String,
    val isActive: Boolean,
    val createdAt: Timestamp,
    val updatedAt: Timestamp? = null
)

// Query
val users: List<User> = select<User> {
    where { User::isActive eq true }
    orderBy(User::createdAt, OrderDirection.DESC)
    limit(10)
}.execute()

// Cada registro é mapeado automaticamente:
// Map("id" → 1, "first_name" → "John", ...) → User(id=1, firstName="John", ...)
```
