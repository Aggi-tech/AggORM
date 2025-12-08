# AggORM API Deprecation Notice

Este documento lista as APIs que estão marcadas para **deprecação** e eventual **remoção** em versões futuras do AggORM.

## Resumo

A partir da versão 1.7.0, o AggORM introduziu uma nova API baseada em **Mirror/Table DSL** que oferece:
- Type-safety em tempo de compilação
- Proteção contra SQL Injection integrada
- Melhor performance e menos uso de reflection
- Suporte a projections (classes e interfaces)

A API antiga baseada em **Entity/KProperty reflection** será descontinuada.

---

## APIs Deprecadas

### 1. DSL baseada em Reflection (`com.aggitech.orm.dsl`)

**Arquivo:** `aggo-core/src/main/kotlin/com/aggitech/orm/dsl/QueryDSL.kt`

| Classe/Função | Status | Substituição |
|---------------|--------|--------------|
| `SelectQueryBuilder<T>` | Deprecado | `TableSelectBuilder` |
| `InsertQueryBuilder<T>` | Deprecado | `TableInsertBuilder` |
| `UpdateQueryBuilder<T>` | Deprecado | `TableUpdateBuilder` |
| `DeleteQueryBuilder<T>` | Deprecado | `TableDeleteBuilder` |
| `WhereBuilder<T>` | Deprecado | `TableWhereBuilder` |
| `SelectBuilder<T>` | Deprecado | `TableSelectFieldBuilder` |
| `OrderByBuilder<T>` | Deprecado | Use `ColumnRef.asc()` / `ColumnRef.desc()` |
| `select<T> { }` (reflection) | Deprecado | `select<Mirror> { }` ou `select(Mirror) { }` |
| `insert<T> { }` (reflection) | Deprecado | `insert<Mirror> { }` ou `insert(Mirror) { }` |
| `update<T> { }` (reflection) | Deprecado | `update<Mirror> { }` ou `update(Mirror) { }` |
| `delete<T> { }` (reflection) | Deprecado | `delete<Mirror> { }` ou `delete(Mirror) { }` |

**Exemplo de migração:**

```kotlin
// API ANTIGA (Deprecada)
val users = select<User> {
    where { User::name eq "John" }
}.executeAsEntities()

// API NOVA (Recomendada)
val users = select<UserMirror> {
    UserMirror.NAME eq "John"
}.executeAs<User>()
```

---

### 2. Query Models (`com.aggitech.orm.query.model`)

**Arquivo:** `aggo-core/src/main/kotlin/com/aggitech/orm/query/model/SelectQuery.kt`

| Classe | Status | Substituição |
|--------|--------|--------------|
| `Query` (sealed class) | Deprecado | Usar builders diretamente |
| `SelectQuery<T>` | Deprecado | `TableSelectBuilder.build()` → `RenderedSql` |
| `InsertQuery<T>` | Deprecado | `TableInsertBuilder.build()` → `RenderedSql` |
| `UpdateQuery<T>` | Deprecado | `TableUpdateBuilder.build()` → `RenderedSql` |
| `DeleteQuery<T>` | Deprecado | `TableDeleteBuilder.build()` → `RenderedSql` |

---

### 3. Renderers Antigos (`com.aggitech.orm.sql.renderer`)

**Arquivos:**
- `aggo-core/src/main/kotlin/com/aggitech/orm/sql/renderer/SelectRenderer.kt`
- `aggo-core/src/main/kotlin/com/aggitech/orm/sql/renderer/InsertRenderer.kt`
- `aggo-core/src/main/kotlin/com/aggitech/orm/sql/renderer/UpdateRenderer.kt`
- `aggo-core/src/main/kotlin/com/aggitech/orm/sql/renderer/DeleteRenderer.kt`
- `aggo-core/src/main/kotlin/com/aggitech/orm/sql/renderer/PredicateRenderer.kt`

| Classe | Status | Substituição |
|--------|--------|--------------|
| `SelectRenderer` | Deprecado | `TableSelectBuilder.build()` |
| `InsertRenderer` | Deprecado | `TableInsertBuilder.build()` |
| `UpdateRenderer` | Deprecado | `TableUpdateBuilder.build()` |
| `DeleteRenderer` | Deprecado | `TableDeleteBuilder.build()` |
| `PredicateRenderer` | Deprecado | Integrado em `TableQueryDSL` |
| `QueryRenderer<T>` interface | Deprecado | Não necessário |

---

### 4. Query Model Components (`com.aggitech.orm.query.model.*`)

**Arquivos:**
- `aggo-core/src/main/kotlin/com/aggitech/orm/query/model/field/SelectField.kt`
- `aggo-core/src/main/kotlin/com/aggitech/orm/query/model/predicate/Predicate.kt`
- `aggo-core/src/main/kotlin/com/aggitech/orm/query/model/operand/Operand.kt`
- `aggo-core/src/main/kotlin/com/aggitech/orm/query/model/ordering/OrderBy.kt`

| Classe | Status | Substituição |
|--------|--------|--------------|
| `SelectField` (sealed) | Deprecado | `SelectExpression` |
| `SelectField.Property` | Deprecado | `SelectExpression.Col` |
| `SelectField.Aggregate` | Deprecado | `SelectExpression.Aggregate` |
| `SelectField.Expression` | Deprecado | `SelectExpression.Raw` |
| `Predicate` (sealed) | Deprecado | `TablePredicate` |
| `Predicate.Comparison` | Deprecado | `TablePredicate.Comparison` |
| `Predicate.And` / `Or` / `Not` | Deprecado | `TablePredicate.And` / `Or` / `Not` |
| `Operand` (sealed) | Deprecado | Valores diretos em predicados |
| `Operand.Property` | Deprecado | `ColumnRef` |
| `Operand.Literal` | Deprecado | Valores diretos |
| `PropertyOrder` | Deprecado | `ColumnOrder` |
| `OrderDirection` | Mantido | Mesmo em `TableQueryDSL` |

---

### 5. Métodos de Execução Antigos

| Método | Status | Substituição |
|--------|--------|--------------|
| `SelectQuery.executeAsEntities()` | Deprecado | `TableSelectBuilder.executeAs<T>()` |
| `SelectQuery.executeAsEntity()` | Deprecado | `TableSelectBuilder.executeOneAs<T>()` |
| `InsertQuery.execute()` | Deprecado | `TableInsertBuilder.execute()` |
| `UpdateQuery.execute()` | Deprecado | `TableUpdateBuilder.execute()` |
| `DeleteQuery.execute()` | Deprecado | `TableDeleteBuilder.execute()` |

---

## Arquivos para Remoção Futura

Os seguintes arquivos/diretórios serão removidos em versões futuras:

```
aggo-core/src/main/kotlin/com/aggitech/orm/
├── dsl/
│   └── QueryDSL.kt                    # REMOVER
├── query/
│   └── model/
│       ├── SelectQuery.kt             # REMOVER
│       ├── field/
│       │   └── SelectField.kt         # REMOVER
│       ├── predicate/
│       │   └── Predicate.kt           # REMOVER
│       ├── operand/
│       │   └── Operand.kt             # REMOVER
│       └── ordering/
│           └── OrderBy.kt             # REMOVER
└── sql/
    └── renderer/
        ├── QueryRenderer.kt           # REMOVER
        ├── SelectRenderer.kt          # REMOVER
        ├── InsertRenderer.kt          # REMOVER
        ├── UpdateRenderer.kt          # REMOVER
        ├── DeleteRenderer.kt          # REMOVER
        └── PredicateRenderer.kt       # REMOVER
```

---

## Timeline de Deprecação

| Versão | Ação |
|--------|------|
| **1.7.0** | APIs marcadas como `@Deprecated` |
| **1.8.0** | Warnings de compilação ativados |
| **2.0.0** | APIs removidas |

---

## Guia de Migração

### SELECT

```kotlin
// ANTES (Deprecado)
val query = select<User> {
    select {
        +User::id
        +User::name
        count(User::id, "total")
    }
    where { User::age gte 18 }
    orderBy { User::name.asc() }
    limit(10)
}
val users = query.executeAsEntities()

// DEPOIS (Recomendado)
val users = select<UserMirror>()
    .select(UserMirror.ID, UserMirror.NAME)
    .select { countAll("total") }
    .where { UserMirror.AGE gte 18 }
    .orderBy(UserMirror.NAME.asc())
    .limit(10)
    .executeAs<User>()
```

### INSERT

```kotlin
// ANTES (Deprecado)
insert<User> {
    User::name to "John"
    User::email to "john@example.com"
}.execute()

// DEPOIS (Recomendado)
insert<UserMirror> {
    UserMirror.NAME to "John"
    UserMirror.EMAIL to "john@example.com"
}.execute()
```

### UPDATE

```kotlin
// ANTES (Deprecado)
update<User> {
    User::name to "Jane"
    where { User::id eq userId }
}.execute()

// DEPOIS (Recomendado)
update<UserMirror> {
    UserMirror.NAME to "Jane"
    where { UserMirror.ID eq userId }
}.execute()
```

### DELETE

```kotlin
// ANTES (Deprecado)
delete<User> {
    where { User::id eq userId }
}.execute()

// DEPOIS (Recomendado)
delete<UserMirror> {
    UserMirror.ID eq userId
}.execute()
```

---

## Benefícios da Nova API

1. **SQL Injection Protection**: Validação automática de identificadores e valores
2. **Type Safety**: Erros de tipo detectados em tempo de compilação
3. **Performance**: Menos uso de reflection em runtime
4. **Projections**: Suporte a DTOs e interfaces via `executeAsProjection<T>()`
5. **Simplicidade**: DSL mais concisa e intuitiva
6. **JOINs Tipados**: Suporte a JOINs entre Mirrors com type-safety

---

## Perguntas Frequentes

### Como gerar os Mirrors?

```bash
./gradlew generateMirrors
```

Ou configure no `application.yml`:

```yaml
aggo:
  orm:
    mirrors:
      base-package: com.example.mirrors
      output-dir: src/main/kotlin
      schema-name: public
```

### Posso usar as duas APIs simultaneamente?

Sim, durante o período de transição ambas APIs funcionam. Porém, recomendamos migrar gradualmente para a nova API.

### Os Mirrors são gerados automaticamente?

Sim, com base no schema do banco de dados. Execute o gerador após alterações no banco.
