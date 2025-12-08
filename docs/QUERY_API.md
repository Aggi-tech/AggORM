# AggORM Query API - Guia Completo

Este documento descreve a nova API de queries do AggORM baseada em **Mirrors** (metadados de tabelas type-safe).

## Sumario

1. [Conceitos Basicos](#conceitos-basicos)
2. [Configuracao Inicial](#configuracao-inicial)
3. [SELECT Queries](#select-queries)
4. [INSERT Queries](#insert-queries)
5. [UPDATE Queries](#update-queries)
6. [DELETE Queries](#delete-queries)
7. [Operadores WHERE](#operadores-where)
8. [JOINs](#joins)
9. [Agregacoes](#agregacoes)
10. [Projections e DTOs](#projections-e-dtos)
11. [Seguranca](#seguranca)

---

## Conceitos Basicos

### O que sao Mirrors?

Mirrors sao classes Kotlin geradas automaticamente que representam tabelas do banco de dados. Elas fornecem:

- **Type-safety**: Erros de tipo detectados em tempo de compilacao
- **Autocompletar**: IDEs sugerem colunas disponiveis
- **Refatoracao segura**: Renomear colunas atualiza todas as referencias

### Exemplo de Mirror Gerado

```kotlin
// Gerado automaticamente - NAO EDITAR
object UsersTable : TableMeta("users") {
    val ID = uuid("id").primaryKey().notNull()
    val NAME = varchar("name", 100).notNull()
    val EMAIL = varchar("email", 255).notNull().unique()
    val AGE = integer("age")
    val STATUS = varchar("status", 20).notNull().default("'ACTIVE'")
    val CREATED_AT = timestamp("created_at").notNull().default("CURRENT_TIMESTAMP")
}
```

### Imports Necessarios

```kotlin
import com.aggitech.orm.table.select
import com.aggitech.orm.table.insert
import com.aggitech.orm.table.update
import com.aggitech.orm.table.delete
import com.aggitech.orm.table.from
import com.aggitech.orm.table.into
import com.aggitech.orm.table.deleteFrom
import com.aggitech.orm.table.asc
import com.aggitech.orm.table.desc
```

---

## Configuracao Inicial

### 1. Gerar os Mirrors

```bash
./gradlew generateMirrors
```

Ou configure no `application.yml`:

```yaml
aggo:
  orm:
    mirrors:
      base-package: com.example.generated.mirrors
      output-dir: src/main/kotlin
      schema-name: public
```

### 2. Configurar Conexao

```kotlin
// Registrar configuracao de conexao
JdbcConnectionManager.register(
    DbConfig(
        host = "localhost",
        port = 5432,
        database = "mydb",
        username = "user",
        password = "pass",
        dialect = PostgresDialect
    )
)
```

---

## SELECT Queries

### Sintaxe Simplificada (Recomendada)

```kotlin
// SELECT * com WHERE
val users = select<UsersTable> {
    UsersTable.STATUS eq "ACTIVE"
}.executeAs<User>()

// SELECT * sem WHERE (todos os registros)
val allUsers = select<UsersTable>().executeAs<User>()

// SELECT com multiplas condicoes
val adults = select<UsersTable> {
    (UsersTable.STATUS eq "ACTIVE") and (UsersTable.AGE gte 18)
}.executeAs<User>()
```

### Sintaxe com Instancia

```kotlin
// Passando o Mirror como parametro
val users = select(UsersTable) {
    UsersTable.NAME eq "John"
}.executeAs<User>()

// Sem WHERE
val allUsers = select(UsersTable).executeAs<User>()
```

### Sintaxe Fluente (Encadeada)

```kotlin
val users = from(UsersTable)
    .select(UsersTable.ID, UsersTable.NAME, UsersTable.EMAIL)
    .where { UsersTable.STATUS eq "ACTIVE" }
    .orderBy(UsersTable.NAME.asc())
    .limit(10)
    .offset(20)
    .executeAs<User>()
```

### Selecionar Colunas Especificas

```kotlin
// Usando varargs
val users = select(UsersTable)
    .select(UsersTable.ID, UsersTable.NAME)
    .executeAs<User>()

// Usando DSL block
val users = select(UsersTable)
    .select {
        +UsersTable.ID
        +UsersTable.NAME
        UsersTable.EMAIL alias "user_email"
    }
    .executeAs<User>()
```

### Ordenacao

```kotlin
// Ordem ASC
val users = select<UsersTable>()
    .orderBy(UsersTable.NAME.asc())
    .executeAs<User>()

// Ordem DESC
val users = select<UsersTable>()
    .orderBy(UsersTable.CREATED_AT.desc())
    .executeAs<User>()

// Multiplas ordenacoes
val users = select<UsersTable>()
    .orderBy(UsersTable.STATUS.asc(), UsersTable.NAME.asc())
    .executeAs<User>()
```

### Paginacao

```kotlin
val page = select<UsersTable>()
    .where { UsersTable.STATUS eq "ACTIVE" }
    .orderBy(UsersTable.ID.asc())
    .limit(20)
    .offset(40)  // Pagina 3 (0-indexed)
    .executeAs<User>()
```

### DISTINCT

```kotlin
val uniqueStatuses = select<UsersTable>()
    .select(UsersTable.STATUS)
    .distinct()
    .execute()  // Retorna List<Map<String, Any?>>
```

### Buscar Um Registro

```kotlin
// Retorna T? (nullable)
val user = select<UsersTable> {
    UsersTable.ID eq userId
}.executeOneAs<User>()

// Usar com seguranca
user?.let { println(it.name) }
```

---

## INSERT Queries

### Sintaxe Simplificada

```kotlin
// INSERT basico
insert<UsersTable> {
    UsersTable.ID to UUID.randomUUID()
    UsersTable.NAME to "John Doe"
    UsersTable.EMAIL to "john@example.com"
    UsersTable.STATUS to "ACTIVE"
}.execute()
```

### Sintaxe com Instancia

```kotlin
insert(UsersTable) {
    UsersTable.NAME to "Jane Doe"
    UsersTable.EMAIL to "jane@example.com"
}.execute()
```

### Sintaxe Fluente

```kotlin
into(UsersTable)
    .values {
        UsersTable.NAME to "Bob"
        UsersTable.EMAIL to "bob@example.com"
    }
    .execute()
```

### INSERT com RETURNING (PostgreSQL)

```kotlin
// Retornar entidade inserida
val user = insert<UsersTable> {
    UsersTable.NAME to "John"
    UsersTable.EMAIL to "john@example.com"
}
.returning(UsersTable.ID, UsersTable.NAME, UsersTable.CREATED_AT)
.executeReturning<User>()

println("Criado: ${user?.id} em ${user?.createdAt}")
```

### INSERT e Obter Chaves Geradas

```kotlin
val keys = insert<UsersTable> {
    UsersTable.NAME to "John"
    UsersTable.EMAIL to "john@example.com"
}.executeReturningKeys()

val generatedId = keys.firstOrNull()
```

---

## UPDATE Queries

### Sintaxe Simplificada

```kotlin
update<UsersTable> {
    UsersTable.NAME to "Jane Updated"
    UsersTable.EMAIL to "jane.updated@example.com"
    UsersTable.STATUS to "INACTIVE"
    where {
        UsersTable.ID eq userId
    }
}.execute()
```

### Sintaxe com Instancia

```kotlin
update(UsersTable) {
    UsersTable.STATUS to "INACTIVE"
    where { UsersTable.ID eq userId }
}.execute()
```

### Sintaxe Fluente

```kotlin
update(UsersTable)
    .set(UsersTable.STATUS, "BANNED")
    .set(UsersTable.NAME, "Banned User")
    .where { UsersTable.ID eq bannedUserId }
    .execute()
```

### UPDATE com RETURNING (PostgreSQL)

```kotlin
val updated = update<UsersTable> {
    UsersTable.STATUS to "ACTIVE"
    where { UsersTable.STATUS eq "PENDING" }
}
.returning(UsersTable.ID, UsersTable.NAME)
.executeReturning<User>()

println("Ativados ${updated.size} usuarios")
```

### UPDATE Multiplos Registros

```kotlin
// Atualiza todos que correspondem ao WHERE
val count = update<UsersTable> {
    UsersTable.STATUS to "ARCHIVED"
    where {
        (UsersTable.STATUS eq "INACTIVE") and
        (UsersTable.CREATED_AT lt LocalDateTime.now().minusYears(1))
    }
}.execute()

println("$count usuarios arquivados")
```

---

## DELETE Queries

### Sintaxe Simplificada

```kotlin
delete<UsersTable> {
    UsersTable.ID eq userId
}.execute()
```

### Sintaxe com Instancia

```kotlin
delete(UsersTable) {
    UsersTable.STATUS eq "DELETED"
}.execute()
```

### Sintaxe Fluente

```kotlin
deleteFrom(UsersTable)
    .where { UsersTable.ID eq userId }
    .execute()
```

### DELETE com RETURNING (PostgreSQL)

```kotlin
val deleted = delete<UsersTable> {
    UsersTable.STATUS eq "TO_DELETE"
}
.returning(UsersTable.ID, UsersTable.EMAIL)
.executeReturning<User>()

// Enviar emails de notificacao
deleted.forEach { user ->
    sendDeletionNotification(user.email)
}
```

---

## Operadores WHERE

### Comparacao

| Operador | Descricao | Exemplo |
|----------|-----------|---------|
| `eq` | Igual (=) | `UsersTable.NAME eq "John"` |
| `ne` | Diferente (!=) | `UsersTable.STATUS ne "DELETED"` |
| `gt` | Maior que (>) | `UsersTable.AGE gt 18` |
| `gte` | Maior ou igual (>=) | `UsersTable.AGE gte 18` |
| `lt` | Menor que (<) | `UsersTable.AGE lt 65` |
| `lte` | Menor ou igual (<=) | `UsersTable.AGE lte 65` |

### Comparacao entre Colunas (para JOINs)

| Operador | Descricao | Exemplo |
|----------|-----------|---------|
| `eqCol` | Igual entre colunas | `OrdersTable.USER_ID eqCol UsersTable.ID` |
| `neCol` | Diferente entre colunas | `A.VALUE neCol B.VALUE` |
| `gtCol` | Maior que entre colunas | `A.PRICE gtCol B.PRICE` |

### NULL

```kotlin
// IS NULL
select<UsersTable> {
    UsersTable.AGE.isNull()
}.executeAs<User>()

// IS NOT NULL
select<UsersTable> {
    UsersTable.EMAIL.isNotNull()
}.executeAs<User>()
```

### LIKE

```kotlin
// Comeca com
select<UsersTable> {
    UsersTable.NAME like "John%"
}.executeAs<User>()

// Contem
select<UsersTable> {
    UsersTable.EMAIL like "%@gmail.com"
}.executeAs<User>()

// Pattern
select<UsersTable> {
    UsersTable.NAME like "%son%"
}.executeAs<User>()
```

### IN / NOT IN

```kotlin
// IN lista
val statuses = listOf("ACTIVE", "PENDING")
select<UsersTable> {
    UsersTable.STATUS inList statuses
}.executeAs<User>()

// NOT IN
select<UsersTable> {
    UsersTable.STATUS notInList listOf("DELETED", "BANNED")
}.executeAs<User>()
```

### BETWEEN

```kotlin
select<UsersTable> {
    UsersTable.AGE.between(18, 65)
}.executeAs<User>()

// Com datas
select<UsersTable> {
    UsersTable.CREATED_AT.between(startDate, endDate)
}.executeAs<User>()
```

### Operadores Logicos

```kotlin
// AND
select<UsersTable> {
    (UsersTable.STATUS eq "ACTIVE") and (UsersTable.AGE gte 18)
}.executeAs<User>()

// OR
select<UsersTable> {
    (UsersTable.STATUS eq "ACTIVE") or (UsersTable.STATUS eq "PENDING")
}.executeAs<User>()

// NOT
select<UsersTable> {
    not(UsersTable.STATUS eq "DELETED")
}.executeAs<User>()

// Combinacoes complexas
select<UsersTable> {
    ((UsersTable.STATUS eq "ACTIVE") and (UsersTable.AGE gte 18)) or
    (UsersTable.STATUS eq "VIP")
}.executeAs<User>()
```

---

## JOINs

### INNER JOIN

```kotlin
from(UsersTable)
    .select(UsersTable.NAME, OrdersTable.TOTAL)
    .innerJoin(OrdersTable) {
        OrdersTable.USER_ID eqCol UsersTable.ID
    }
    .where { UsersTable.STATUS eq "ACTIVE" }
    .executeAs<UserOrder>()
```

### LEFT JOIN

```kotlin
from(UsersTable)
    .select(UsersTable.NAME, OrdersTable.TOTAL)
    .leftJoin(OrdersTable) {
        OrdersTable.USER_ID eqCol UsersTable.ID
    }
    .executeAs<UserOrder>()  // Users sem orders terao total = null
```

### RIGHT JOIN

```kotlin
from(OrdersTable)
    .rightJoin(UsersTable) {
        OrdersTable.USER_ID eqCol UsersTable.ID
    }
    .executeAs<OrderWithUser>()
```

### FULL OUTER JOIN

```kotlin
from(UsersTable)
    .fullJoin(ProfilesTable) {
        ProfilesTable.USER_ID eqCol UsersTable.ID
    }
    .executeAs<UserProfile>()
```

### Multiplos JOINs

```kotlin
from(UsersTable)
    .select(UsersTable.NAME, OrdersTable.TOTAL, ProductsTable.NAME)
    .innerJoin(OrdersTable) {
        OrdersTable.USER_ID eqCol UsersTable.ID
    }
    .innerJoin(ProductsTable) {
        ProductsTable.ID eqCol OrdersTable.PRODUCT_ID
    }
    .where { UsersTable.STATUS eq "ACTIVE" }
    .executeAs<UserOrderProduct>()
```

---

## Agregacoes

### COUNT

```kotlin
// COUNT(*)
val result = from(UsersTable)
    .select { countAll("total") }
    .execute()

val total = result.firstOrNull()?.get("total") as? Long

// COUNT(column)
from(UsersTable)
    .select { count(UsersTable.EMAIL, "email_count") }
    .execute()

// COUNT(DISTINCT column)
from(UsersTable)
    .select { countDistinct(UsersTable.STATUS, "unique_statuses") }
    .execute()
```

### SUM / AVG / MIN / MAX

```kotlin
from(OrdersTable)
    .select {
        sum(OrdersTable.TOTAL, "total_sales")
        avg(OrdersTable.TOTAL, "avg_order")
        min(OrdersTable.TOTAL, "min_order")
        max(OrdersTable.TOTAL, "max_order")
    }
    .where { OrdersTable.STATUS eq "COMPLETED" }
    .execute()
```

### GROUP BY

```kotlin
from(UsersTable)
    .select {
        +UsersTable.STATUS
        countAll("count")
    }
    .groupBy(UsersTable.STATUS)
    .execute()

// Resultado:
// [
//   {"status": "ACTIVE", "count": 150},
//   {"status": "INACTIVE", "count": 30},
//   {"status": "PENDING", "count": 20}
// ]
```

### HAVING

```kotlin
from(UsersTable)
    .select {
        +UsersTable.STATUS
        countAll("count")
    }
    .groupBy(UsersTable.STATUS)
    .having { countAll("count") gt 10 }  // Apenas status com mais de 10 usuarios
    .execute()
```

### Agregacoes com GROUP BY Completo

```kotlin
// Vendas por mes
from(OrdersTable)
    .select {
        raw("DATE_TRUNC('month', created_at)", "month")
        sum(OrdersTable.TOTAL, "monthly_sales")
        countAll("order_count")
    }
    .where { OrdersTable.STATUS eq "COMPLETED" }
    .groupBy(/* use raw for date_trunc */)
    .orderBy(/* month desc */)
    .execute()
```

---

## Projections e DTOs

### Data Class Projection

```kotlin
// DTO com subset de campos
data class UserSummary(
    val id: UUID,
    val name: String,
    val email: String
)

val summaries = from(UsersTable)
    .select(UsersTable.ID, UsersTable.NAME, UsersTable.EMAIL)
    .where { UsersTable.STATUS eq "ACTIVE" }
    .executeAsProjection<UserSummary>()
```

### Interface Projection

```kotlin
// Interface (usa Java Proxy internamente)
interface UserNameOnly {
    val name: String
}

val names = from(UsersTable)
    .select(UsersTable.NAME)
    .executeAsProjection<UserNameOnly>()

names.forEach { println(it.name) }
```

### Projection com JOIN

```kotlin
data class UserWithOrders(
    val userName: String,  // Mapeia de user_name (alias)
    val orderTotal: BigDecimal
)

val result = from(UsersTable)
    .select {
        UsersTable.NAME alias "user_name"
        OrdersTable.TOTAL alias "order_total"
    }
    .innerJoin(OrdersTable) {
        OrdersTable.USER_ID eqCol UsersTable.ID
    }
    .executeAsProjection<UserWithOrders>()
```

### Mapeamento Automatico

O `EntityMapper` converte automaticamente:
- `snake_case` -> `camelCase` (colunas para propriedades)
- Tipos numericos (Int, Long, Double, Float)
- Boolean
- String
- Enums (por nome)
- UUID
- Datas (LocalDateTime, LocalDate, etc.)

---

## Seguranca

### Protecao contra SQL Injection

A API valida automaticamente:

1. **Identificadores** (tabelas, colunas, aliases)
   - Apenas caracteres alfanumericos e underscore
   - Deve comecar com letra ou underscore
   - Maximo 128 caracteres

2. **Valores** (parametros)
   - Sempre usam prepared statements (`?`)
   - Validacao adicional contra padroes perigosos

3. **Patterns LIKE**
   - Validados contra injection

4. **Raw SQL** (quando usado)
   - Bloqueado para DDL (CREATE, DROP, ALTER)
   - Bloqueado para DML perigoso (TRUNCATE, GRANT)

### Exemplo de Bloqueio

```kotlin
// ERRO: InvalidIdentifierException
val malicious = "users; DROP TABLE users; --"
select(UsersTable)
    .where { UsersTable.NAME eq malicious }  // Valor OK (parametrizado)
    .execute()

// ERRO: SqlInjectionException
select(UsersTable)
    .select { raw("1; DROP TABLE users;--", "x") }  // Bloqueado
    .execute()
```

---

## Exemplos Completos

### CRUD Basico

```kotlin
// CREATE
val userId = UUID.randomUUID()
insert<UsersTable> {
    UsersTable.ID to userId
    UsersTable.NAME to "John Doe"
    UsersTable.EMAIL to "john@example.com"
    UsersTable.STATUS to "ACTIVE"
}.execute()

// READ
val user = select<UsersTable> {
    UsersTable.ID eq userId
}.executeOneAs<User>()

// UPDATE
update<UsersTable> {
    UsersTable.NAME to "John Updated"
    where { UsersTable.ID eq userId }
}.execute()

// DELETE
delete<UsersTable> {
    UsersTable.ID eq userId
}.execute()
```

### Busca com Filtros Dinamicos

```kotlin
fun searchUsers(
    name: String? = null,
    status: String? = null,
    minAge: Int? = null,
    maxAge: Int? = null
): List<User> {
    var query = select(UsersTable)

    // Adiciona filtros condicionalmente
    val predicates = mutableListOf<TablePredicate>()

    name?.let { predicates.add(TableWhereBuilder().run { UsersTable.NAME like "%$it%" }) }
    status?.let { predicates.add(TableWhereBuilder().run { UsersTable.STATUS eq it }) }
    minAge?.let { predicates.add(TableWhereBuilder().run { UsersTable.AGE gte it }) }
    maxAge?.let { predicates.add(TableWhereBuilder().run { UsersTable.AGE lte it }) }

    if (predicates.isNotEmpty()) {
        query = query.where {
            predicates.reduce { acc, pred -> acc and pred }
        }
    }

    return query.executeAs<User>()
}
```

### Relatorio com Agregacoes

```kotlin
data class SalesReport(
    val month: String,
    val totalSales: BigDecimal,
    val orderCount: Long,
    val avgOrderValue: BigDecimal
)

val report = from(OrdersTable)
    .select {
        raw("TO_CHAR(created_at, 'YYYY-MM')", "month")
        sum(OrdersTable.TOTAL, "total_sales")
        countAll("order_count")
        avg(OrdersTable.TOTAL, "avg_order_value")
    }
    .where {
        (OrdersTable.STATUS eq "COMPLETED") and
        OrdersTable.CREATED_AT.between(startDate, endDate)
    }
    .groupBy(/* month */)
    .orderBy(/* month desc */)
    .executeAsProjection<SalesReport>()
```

---

## Referencia Rapida

| Operacao | Sintaxe |
|----------|---------|
| SELECT * | `select<Table>().executeAs<Entity>()` |
| SELECT com WHERE | `select<Table> { col eq value }.executeAs<Entity>()` |
| SELECT colunas | `from(Table).select(col1, col2).executeAs<Entity>()` |
| INSERT | `insert<Table> { col to value }.execute()` |
| UPDATE | `update<Table> { col to value; where { id eq x } }.execute()` |
| DELETE | `delete<Table> { id eq x }.execute()` |
| JOIN | `from(A).innerJoin(B) { B.FK eqCol A.PK }` |
| ORDER BY | `.orderBy(col.asc(), col2.desc())` |
| LIMIT/OFFSET | `.limit(10).offset(20)` |
| GROUP BY | `.groupBy(col1, col2)` |
| HAVING | `.having { countAll("c") gt 5 }` |
| DISTINCT | `.distinct()` |
