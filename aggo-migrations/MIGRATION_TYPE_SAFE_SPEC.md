# Especificação Técnica: Migrations Type-Safe

## Objetivo

Implementar suporte completo a type-safety no sistema de migrations do AggORM usando `KClass` e `KProperty`, eliminando uso de Strings para referências de tabelas e colunas.

## Sintaxe Desejada

### Criar Tabela
```kotlin
createTable(Post::class) {
    column { uuid(Post::id).primaryKey() }
    column { uuid(Post::userId).notNull() }
    column { varchar(Post::title, 200).notNull() }
    column { text(Post::content).notNull() }
    column { timestamp(Post::createdAt).notNull().default("CURRENT_TIMESTAMP") }

    foreignKey(Post::userId, User::id, onDelete = CascadeType.CASCADE)

    index(listOf(Post::userId), name = "idx_posts_user_id")
    index(listOf(Post::createdAt), name = "idx_posts_created_at")
}
```

### Alterar Tabela
```kotlin
transaction {
    updateTable(User::class) {
        column { varchar(User::email, 255).unique().notNull() }
        alterColumn(User::name).notNull()
        dropColumn(User::oldField)
    }
}
```

### Drop Tabela
```kotlin
dropTable(Post::class)
```

## Componentes a Implementar

### 1. TypedTableBuilder<T>

Classe builder para criação de tabelas com type-safety.

**Localização:** `aggo-migrations/src/main/kotlin/com/aggitech/orm/migrations/core/Migration.kt`

**Responsabilidades:**
- Armazenar definições de colunas, FKs, indexes
- Inferir nome da tabela de `KClass<T>`
- Validar que properties pertencem à entity class
- Construir `MigrationOperation.CreateTable`

**Métodos:**
```kotlin
class TypedTableBuilder<T : Any>(
    private val entityClass: KClass<T>,
    private val tableName: String,
    private val schema: String = "public"
) {
    fun column(block: TypedColumnBuilder<T>.() -> ColumnDefinition)
    fun <R> foreignKey(source: KProperty1<T, R>, target: KProperty1<*, R>, onDelete: CascadeType? = null, onUpdate: CascadeType? = null)
    fun <R> index(properties: List<KProperty1<T, R>>, name: String? = null, unique: Boolean = false)
    fun unique(vararg properties: KProperty1<T, *>, name: String? = null)
    fun build(): MigrationOperation.CreateTable
}
```

### 2. TypedColumnBuilder<T>

Builder para definir colunas usando KProperty.

**Métodos principais (já existem, mas precisam retornar ColumnDefinition):**
```kotlin
class TypedColumnBuilder<T : Any> {
    fun <R> uuid(property: KProperty1<T, R>): ColumnDefinition
    fun <R> varchar(property: KProperty1<T, R>, length: Int = 255): ColumnDefinition
    fun <R> text(property: KProperty1<T, R>): ColumnDefinition
    fun <R> integer(property: KProperty1<T, R>): ColumnDefinition
    fun <R> bigInteger(property: KProperty1<T, R>): ColumnDefinition
    fun <R> boolean(property: KProperty1<T, R>): ColumnDefinition
    fun <R> timestamp(property: KProperty1<T, R>): ColumnDefinition
    fun <R> decimal(property: KProperty1<T, R>, precision: Int, scale: Int): ColumnDefinition
    // ... todos os tipos
}
```

**Extensões em ColumnDefinition:**
```kotlin
fun ColumnDefinition.notNull(): ColumnDefinition
fun ColumnDefinition.unique(): ColumnDefinition
fun ColumnDefinition.primaryKey(): ColumnDefinition
fun ColumnDefinition.autoIncrement(): ColumnDefinition
fun ColumnDefinition.default(value: String): ColumnDefinition
```

### 3. TypedTableUpdateBuilder<T>

Builder para alterações de tabela (ADD/ALTER/DROP columns).

```kotlin
class TypedTableUpdateBuilder<T : Any>(
    private val entityClass: KClass<T>,
    private val tableName: String,
    private val schema: String = "public"
) {
    fun column(block: TypedColumnBuilder<T>.() -> ColumnDefinition): TypedTableUpdateBuilder<T>
    fun <R> alterColumn(property: KProperty1<T, R>): ColumnAlterBuilder
    fun <R> dropColumn(property: KProperty1<T, R>): TypedTableUpdateBuilder<T>
    fun buildOperations(): List<MigrationOperation>
}
```

### 4. Métodos Protected na Migration

Adicionar à classe abstrata `Migration`:

```kotlin
abstract class Migration {
    // ... código existente ...

    // Type-safe table creation
    protected fun <T : Any> createTable(
        entityClass: KClass<T>,
        schema: String = "public",
        block: TypedTableBuilder<T>.() -> Unit
    ) {
        val tableName = EntityRegistry.resolveTable(entityClass)
        val builder = TypedTableBuilder(entityClass, tableName, schema)
        builder.block()
        operations.add(builder.build())
    }

    // Type-safe table updates
    protected fun <T : Any> updateTable(
        entityClass: KClass<T>,
        schema: String = "public",
        block: TypedTableUpdateBuilder<T>.() -> Unit
    ) {
        val tableName = EntityRegistry.resolveTable(entityClass)
        val builder = TypedTableUpdateBuilder(entityClass, tableName, schema)
        builder.block()
        operations.addAll(builder.buildOperations())
    }

    // Type-safe table drop
    protected fun <T : Any> dropTable(
        entityClass: KClass<T>,
        schema: String = "public",
        ifExists: Boolean = true
    ) {
        val tableName = EntityRegistry.resolveTable(entityClass)
        operations.add(MigrationOperation.DropTable(tableName, schema, ifExists))
    }

    // Transaction wrapper
    protected fun transaction(block: () -> Unit) {
        // Simplesmente executa o bloco - transações são gerenciadas pelo executor
        block()
    }
}
```

## Mudanças no ColumnDefinition

Tornar `ColumnDefinition` imutável e adicionar métodos de cópia:

```kotlin
data class ColumnDefinition(
    val name: String,
    val type: ColumnType,
    val nullable: Boolean = true,
    val unique: Boolean = false,
    val primaryKey: Boolean = false,
    val autoIncrement: Boolean = false,
    val defaultValue: String? = null,
    val length: Int? = null,
    val precision: Int? = null,
    val scale: Int? = null
) {
    fun notNull(): ColumnDefinition = copy(nullable = false)
    fun unique(): ColumnDefinition = copy(unique = true)
    fun primaryKey(): ColumnDefinition = copy(primaryKey = true)
    fun autoIncrement(): ColumnDefinition = copy(autoIncrement = true)
    fun default(value: String): ColumnDefinition = copy(defaultValue = value)
}
```

## Foreign Keys Tipadas

O sistema deve inferir automaticamente a tabela referenciada:

```kotlin
fun <R> foreignKey(
    sourceProperty: KProperty1<T, R>,
    targetProperty: KProperty1<*, R>,
    onDelete: CascadeType? = null,
    onUpdate: CascadeType? = null
) {
    val sourceColumn = PropertyUtils.getColumnName(sourceProperty)

    // Infere entity class da property
    val targetEntityClass = targetProperty.declaringClass
    val targetTable = EntityRegistry.resolveTable(targetEntityClass)
    val targetColumn = PropertyUtils.getColumnName(targetProperty)

    foreignKeys.add(ForeignKeyDefinition(
        name = "fk_${tableName}_${sourceColumn}",
        columnName = sourceColumn,
        referencedTable = targetTable,
        referencedColumn = targetColumn,
        onDelete = onDelete,
        onUpdate = onUpdate
    ))
}
```

## Compatibilidade

**Manter métodos antigos** baseados em String para não quebrar migrations existentes:

- `createTable(name: String, schema: String, block: TableBuilder.() -> Unit)`
- `addColumn(tableName: String, schema: String, block: ColumnBuilder.() -> Unit)`
- `dropTable(name: String, schema: String, ifExists: Boolean)`

## Testes Necessários

### Teste 1: Criar Tabela Tipada
```kotlin
class V001_CreateUserTable : Migration() {
    override fun up() {
        createTable(User::class) {
            column { bigInteger(User::id).primaryKey().autoIncrement() }
            column { varchar(User::name, 100).notNull() }
            column { varchar(User::email, 255).notNull().unique() }
            column { timestamp(User::createdAt).notNull().default("CURRENT_TIMESTAMP") }
        }
    }

    override fun down() {
        dropTable(User::class)
    }
}
```

### Teste 2: Foreign Key Tipada
```kotlin
class V002_CreatePostTable : Migration() {
    override fun up() {
        createTable(Post::class) {
            column { uuid(Post::id).primaryKey() }
            column { uuid(Post::userId).notNull() }
            column { varchar(Post::title, 200).notNull() }

            foreignKey(Post::userId, User::id, onDelete = CascadeType.CASCADE)

            index(listOf(Post::userId), name = "idx_posts_user_id")
        }
    }

    override fun down() {
        dropTable(Post::class)
    }
}
```

### Teste 3: Atualizar Tabela
```kotlin
class V003_AddEmailToUser : Migration() {
    override fun up() {
        transaction {
            updateTable(User::class) {
                column { varchar(User::email, 255).unique() }
            }
        }
    }

    override fun down() {
        transaction {
            updateTable(User::class) {
                dropColumn(User::email)
            }
        }
    }
}
```

## Validações

1. **Compile-time safety**: Property deve pertencer à entity class
2. **Runtime validation**: Verificar que entity class tem anotações corretas
3. **Checksum consistency**: Checksums devem ser consistentes com migrations antigas
4. **SQL rendering**: Renderers existentes devem funcionar sem modificação

## Benefícios

- Type-safety completa: erros de compilação para properties inexistentes
- Refactoring-safe: renomear property atualiza migrations automaticamente
- IDE support: autocomplete, go-to-definition, find usages
- Menos verboso: não precisa especificar nome da tabela/coluna
- Foreign keys inteligentes: infere tabelas automaticamente
- Defaults sensatos: nullable por padrão, unique = false

## Riscos e Mitigação

**Risco 1:** Breaking changes em migrations existentes
- **Mitigação:** Manter métodos antigos baseados em String

**Risco 2:** Performance de reflexão
- **Mitigação:** Cache de metadata no EntityRegistry

**Risco 3:** Complexidade adicional
- **Mitigação:** Documentação clara e exemplos abundantes
