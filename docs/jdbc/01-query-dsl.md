# Query DSL - Type-Safe Query Builder

O AggORM fornece uma DSL (Domain-Specific Language) type-safe para construção de queries SQL usando Kotlin.

## Visão Geral

A Query DSL permite construir queries SQL de forma segura em tempo de compilação, usando referências diretas às propriedades das entidades Kotlin através de `KProperty`.

## Principais Builders

### SelectQueryBuilder

Constrói queries SELECT com suporte completo a filtros, ordenação, agregações e paginação.

```kotlin
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val age: Int
)

// SELECT básico
val users = select<User> {
    where { User::email eq "john@example.com" }
}.execute()

// SELECT com múltiplos filtros
val adults = select<User> {
    where {
        (User::age gte 18) and (User::name like "%John%")
    }
    orderBy(User::name, OrderDirection.ASC)
    limit(10)
    offset(0)
}.execute()

// SELECT com campos específicos
val names = select<User> {
    select(User::name)
    where { User::age gte 18 }
}.execute()
```

### InsertQueryBuilder

Insere novos registros no banco de dados.

```kotlin
// Inserção simples
val user = User(0, "John Doe", "john@example.com", 30)
insert(user).execute()

// Inserção com retorno de chaves geradas
val generatedId = insert(user).executeReturningKeys()

// Inserção com DSL
insert<User> {
    set(User::name, "Jane Doe")
    set(User::email, "jane@example.com")
    set(User::age, 25)
}.execute()
```

### UpdateQueryBuilder

Atualiza registros existentes.

```kotlin
// Atualização com WHERE
update<User> {
    set(User::name, "John Updated")
    set(User::age, 31)
    where { User::id eq 1L }
}.execute()

// Atualização em lote
update<User> {
    set(User::age, 18)
    where { User::age lt 18 }
}.execute()
```

### DeleteQueryBuilder

Remove registros do banco de dados.

```kotlin
// Deletar por ID
delete<User> {
    where { User::id eq 1L }
}.execute()

// Deletar com condições complexas
delete<User> {
    where {
        (User::age lt 18) and (User::email like "%@temp.com")
    }
}.execute()
```

## WhereBuilder - Operadores Suportados

### Operadores de Comparação

```kotlin
where {
    User::age eq 18        // age = 18
    User::age ne 18        // age != 18
    User::age gt 18        // age > 18
    User::age gte 18       // age >= 18
    User::age lt 18        // age < 18
    User::age lte 18       // age <= 18
}
```

### Operadores de String

```kotlin
where {
    User::name like "%John%"       // name LIKE '%John%'
    User::name notLike "%Admin%"   // name NOT LIKE '%Admin%'
}
```

### Operadores de Coleção

```kotlin
where {
    User::id inList listOf(1L, 2L, 3L)           // id IN (1, 2, 3)
    User::status notInList listOf("banned")      // status NOT IN ('banned')
}
```

### Operadores de Nulabilidade

```kotlin
where {
    User::deletedAt.isNull()       // deleted_at IS NULL
    User::deletedAt.isNotNull()    // deleted_at IS NOT NULL
}
```

### Operador de Range

```kotlin
where {
    User::age.between(18, 65)      // age BETWEEN 18 AND 65
}
```

### Operadores Lógicos

```kotlin
where {
    // AND
    (User::age gte 18) and (User::age lte 65)

    // OR
    (User::role eq "admin") or (User::role eq "moderator")

    // NOT
    not(User::status eq "banned")

    // Combinação complexa
    ((User::age gte 18) and (User::verified eq true)) or (User::role eq "admin")
}
```

## SelectBuilder - Agregações e Funções

### Funções de Agregação

```kotlin
// COUNT(*)
val totalUsers = select<User> {
    countAll()
}.execute()

// COUNT(DISTINCT email)
val uniqueEmails = select<User> {
    count(User::email)
    distinct()
}.execute()

// SUM
val totalAge = select<User> {
    sum(User::age)
}.execute()

// AVG
val averageAge = select<User> {
    avg(User::age)
}.execute()

// MIN e MAX
val youngestAge = select<User> {
    min(User::age)
}.execute()

val oldestAge = select<User> {
    max(User::age)
}.execute()
```

### GROUP BY e HAVING

```kotlin
// Agrupar por campo
select<User> {
    select(User::role)
    count(User::id)
    groupBy(User::role)
}.execute()

// GROUP BY com HAVING
select<User> {
    select(User::role)
    count(User::id)
    groupBy(User::role)
    having { count(User::id) gt 10 }
}.execute()
```

## OrderByBuilder - Ordenação

```kotlin
// Ordem ascendente
select<User> {
    orderBy(User::name, OrderDirection.ASC)
}.execute()

// Ordem descendente
select<User> {
    orderBy(User::age, OrderDirection.DESC)
}.execute()

// Múltiplas ordenações
select<User> {
    orderBy(User::role, OrderDirection.ASC)
    orderBy(User::name, OrderDirection.ASC)
}.execute()
```

## Paginação

```kotlin
// Página 1 (primeiros 10 registros)
select<User> {
    limit(10)
    offset(0)
}.execute()

// Página 2 (próximos 10 registros)
select<User> {
    limit(10)
    offset(10)
}.execute()

// Helper para paginação
fun getUsersPage(page: Int, pageSize: Int) = select<User> {
    limit(pageSize)
    offset(page * pageSize)
    orderBy(User::id, OrderDirection.ASC)
}.execute()
```

## Type Safety

A DSL é completamente type-safe:

```kotlin
// [OK] Compila - tipo correto
where { User::age eq 18 }

// [AVOID] Não compila - tipo incompatível
where { User::age eq "eighteen" }

// [OK] Compila - operador válido para String
where { User::name like "%John%" }

// [AVOID] Não compila - operador inválido para Int
where { User::age like 18 }
```

## Benefícios

- **Type Safety**: Erros detectados em tempo de compilação
- **Refactoring Seguro**: Renomear propriedades atualiza automaticamente queries
- **IDE Support**: Auto-complete e navegação para propriedades
- **Prevenção de SQL Injection**: Uso automático de PreparedStatements
- **Legibilidade**: Código Kotlin idiomático
