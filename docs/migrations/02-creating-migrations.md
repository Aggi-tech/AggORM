# Criando Migrations

Este guia detalha como criar migrations usando a DSL type-safe do AggORM.

## Estrutura de uma Migration

### Classe Base

```kotlin
import com.aggitech.orm.migrations.core.Migration

class V001_CreateUsersTable : Migration() {

    override fun up() {
        // Operações para aplicar a migration
    }

    override fun down() {
        // Operações para reverter a migration
    }
}
```

### Propriedades Automáticas

A classe `Migration` extrai automaticamente metadados do nome:

```kotlin
class V001_20241206120000_CreateUsersTable : Migration()

// Propriedades extraídas:
// version = 1
// timestamp = "20241206120000"
// description = "CreateUsersTable"
```

## Criando Tabelas

### createTable

```kotlin
override fun up() {
    createTable<User> {
        column(User::id).bigInteger().primaryKey().autoIncrement()
        column(User::name).varchar(100).notNull()
        column(User::email).varchar(255).notNull().unique()
        column(User::age).integer()
        column(User::active).boolean().notNull().default("true")
        column(User::createdAt).timestamp().notNull().default("CURRENT_TIMESTAMP")
    }
}
```

### SQL Gerado (PostgreSQL)

```sql
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    age INTEGER,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## Tipos de Colunas

### Strings

```kotlin
createTable<Example> {
    // VARCHAR com tamanho
    column(Example::name).varchar(100)

    // CHAR com tamanho fixo
    column(Example::code).char(10)

    // TEXT (sem limite)
    column(Example::description).text()
}
```

### Números

```kotlin
createTable<Example> {
    // INTEGER (32-bit)
    column(Example::count).integer()

    // BIGINT (64-bit)
    column(Example::id).bigInteger()

    // SMALLINT (16-bit)
    column(Example::priority).smallInteger()

    // DECIMAL com precisão
    column(Example::price).decimal(10, 2)

    // FLOAT
    column(Example::rate).float()

    // DOUBLE
    column(Example::amount).double()
}
```

### Datas e Tempo

```kotlin
createTable<Example> {
    // DATE (apenas data)
    column(Example::birthDate).date()

    // TIME (apenas hora)
    column(Example::startTime).time()

    // TIMESTAMP (data e hora)
    column(Example::createdAt).timestamp()
}
```

### Booleanos

```kotlin
createTable<Example> {
    column(Example::active).boolean()
    column(Example::verified).boolean().notNull().default("false")
}
```

### Binários

```kotlin
createTable<Example> {
    // BINARY com tamanho
    column(Example::hash).binary(32)

    // BLOB (sem limite)
    column(Example::content).blob()
}
```

### JSON

```kotlin
createTable<Example> {
    // JSON
    column(Example::metadata).json()

    // JSONB (PostgreSQL - indexável)
    column(Example::config).jsonb()
}
```

### UUID

```kotlin
createTable<Example> {
    column(Example::id).uuid().primaryKey().default("gen_random_uuid()")
}
```

## Constraints

### Primary Key

```kotlin
createTable<User> {
    // Primary key simples
    column(User::id).bigInteger().primaryKey().autoIncrement()

    // Ou Primary key composta
    primaryKey(User::tenantId, User::id)
}
```

### Not Null

```kotlin
createTable<User> {
    column(User::name).varchar(100).notNull()
    column(User::email).varchar(255).notNull()
}
```

### Unique

```kotlin
createTable<User> {
    // Unique em coluna única
    column(User::email).varchar(255).unique()

    // Unique composto
    unique(User::tenantId, User::email)
}
```

### Default

```kotlin
createTable<User> {
    // Valor literal
    column(User::active).boolean().default("true")
    column(User::role).varchar(20).default("'user'")

    // Função SQL
    column(User::createdAt).timestamp().default("CURRENT_TIMESTAMP")
    column(User::id).uuid().default("gen_random_uuid()")
}
```

### Auto Increment

```kotlin
createTable<User> {
    // GENERATED ALWAYS AS IDENTITY (PostgreSQL)
    column(User::id).bigInteger().autoIncrement()

    // Com primary key
    column(User::id).bigInteger().primaryKey().autoIncrement()
}
```

## Foreign Keys

### Referência Simples

```kotlin
createTable<Post> {
    column(Post::id).bigInteger().primaryKey().autoIncrement()
    column(Post::userId).bigInteger().notNull()

    // Foreign key para User.id
    foreignKey(Post::userId)
        .references<User>(User::id)
}
```

### Com Ações de Cascade

```kotlin
createTable<Post> {
    column(Post::userId).bigInteger().notNull()

    foreignKey(Post::userId)
        .references<User>(User::id)
        .onDelete(CascadeType.CASCADE)
        .onUpdate(CascadeType.CASCADE)
}
```

### Tipos de Cascade

| Tipo | Descrição |
|------|-----------|
| `CASCADE` | Propaga a ação para registros relacionados |
| `RESTRICT` | Impede a ação se houver dependências |
| `SET_NULL` | Define como NULL |
| `SET_DEFAULT` | Define como valor default |
| `NO_ACTION` | Nenhuma ação (verifica ao final da transação) |

### Foreign Key Composta

```kotlin
createTable<OrderItem> {
    column(OrderItem::orderId).bigInteger().notNull()
    column(OrderItem::productId).bigInteger().notNull()

    foreignKey(OrderItem::orderId, OrderItem::productId)
        .references<Product>(Product::orderId, Product::id)
}
```

## Índices

### Criar Índice

```kotlin
override fun up() {
    createIndex<User>("idx_users_email") {
        columns(User::email)
    }
}
```

### Índice Composto

```kotlin
createIndex<User>("idx_users_name_email") {
    columns(User::name, User::email)
}
```

### Índice Único

```kotlin
createIndex<User>("idx_users_email_unique") {
    columns(User::email)
    unique()
}
```

## Alterando Tabelas

### Adicionar Coluna

```kotlin
override fun up() {
    alterTable<User> {
        addColumn(User::phoneNumber).varchar(20)
        addColumn(User::verifiedAt).timestamp()
    }
}

override fun down() {
    alterTable<User> {
        dropColumn(User::phoneNumber)
        dropColumn(User::verifiedAt)
    }
}
```

### Remover Coluna

```kotlin
override fun up() {
    alterTable<User> {
        dropColumn(User::legacyField)
    }
}
```

### Renomear Coluna

```kotlin
override fun up() {
    renameColumn<User>(User::oldName, "new_name")
}
```

## Removendo Tabelas

### Drop Table

```kotlin
override fun up() {
    dropTable<LegacyUser>()
}

// Com IF EXISTS
override fun up() {
    dropTable<LegacyUser>(ifExists = true)
}
```

### Renomear Tabela

```kotlin
override fun up() {
    renameTable("old_users", "legacy_users")
}
```

## SQL Customizado

### Execute SQL

Para operações não suportadas pela DSL:

```kotlin
override fun up() {
    executeSql("""
        CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
    """)

    executeSql("""
        CREATE OR REPLACE FUNCTION update_modified_column()
        RETURNS TRIGGER AS $$
        BEGIN
            NEW.updated_at = CURRENT_TIMESTAMP;
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
    """)
}
```

## Exemplos Completos

### Sistema de Blog

```kotlin
// V001 - Usuários
class V001_CreateUsersTable : Migration() {
    override fun up() {
        createTable<User> {
            column(User::id).bigInteger().primaryKey().autoIncrement()
            column(User::username).varchar(50).notNull().unique()
            column(User::email).varchar(255).notNull().unique()
            column(User::passwordHash).varchar(255).notNull()
            column(User::displayName).varchar(100)
            column(User::bio).text()
            column(User::avatarUrl).varchar(500)
            column(User::role).varchar(20).notNull().default("'user'")
            column(User::active).boolean().notNull().default("true")
            column(User::createdAt).timestamp().notNull().default("CURRENT_TIMESTAMP")
            column(User::updatedAt).timestamp()
        }

        createIndex<User>("idx_users_email") {
            columns(User::email)
        }
    }

    override fun down() {
        dropTable<User>()
    }
}

// V002 - Categorias
class V002_CreateCategoriesTable : Migration() {
    override fun up() {
        createTable<Category> {
            column(Category::id).bigInteger().primaryKey().autoIncrement()
            column(Category::name).varchar(100).notNull()
            column(Category::slug).varchar(100).notNull().unique()
            column(Category::description).text()
            column(Category::parentId).bigInteger()

            foreignKey(Category::parentId)
                .references<Category>(Category::id)
                .onDelete(CascadeType.SET_NULL)
        }
    }

    override fun down() {
        dropTable<Category>()
    }
}

// V003 - Posts
class V003_CreatePostsTable : Migration() {
    override fun up() {
        createTable<Post> {
            column(Post::id).bigInteger().primaryKey().autoIncrement()
            column(Post::authorId).bigInteger().notNull()
            column(Post::categoryId).bigInteger()
            column(Post::title).varchar(200).notNull()
            column(Post::slug).varchar(200).notNull().unique()
            column(Post::excerpt).varchar(500)
            column(Post::content).text().notNull()
            column(Post::featuredImage).varchar(500)
            column(Post::status).varchar(20).notNull().default("'draft'")
            column(Post::views).integer().notNull().default("0")
            column(Post::publishedAt).timestamp()
            column(Post::createdAt).timestamp().notNull().default("CURRENT_TIMESTAMP")
            column(Post::updatedAt).timestamp()

            foreignKey(Post::authorId)
                .references<User>(User::id)
                .onDelete(CascadeType.CASCADE)

            foreignKey(Post::categoryId)
                .references<Category>(Category::id)
                .onDelete(CascadeType.SET_NULL)
        }

        createIndex<Post>("idx_posts_author") {
            columns(Post::authorId)
        }

        createIndex<Post>("idx_posts_status_published") {
            columns(Post::status, Post::publishedAt)
        }
    }

    override fun down() {
        dropTable<Post>()
    }
}

// V004 - Tags (Many-to-Many)
class V004_CreateTagsTable : Migration() {
    override fun up() {
        createTable<Tag> {
            column(Tag::id).bigInteger().primaryKey().autoIncrement()
            column(Tag::name).varchar(50).notNull().unique()
            column(Tag::slug).varchar(50).notNull().unique()
        }

        // Tabela de junção
        executeSql("""
            CREATE TABLE post_tags (
                post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
                tag_id BIGINT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                PRIMARY KEY (post_id, tag_id)
            )
        """)
    }

    override fun down() {
        executeSql("DROP TABLE IF EXISTS post_tags")
        dropTable<Tag>()
    }
}

// V005 - Comentários
class V005_CreateCommentsTable : Migration() {
    override fun up() {
        createTable<Comment> {
            column(Comment::id).bigInteger().primaryKey().autoIncrement()
            column(Comment::postId).bigInteger().notNull()
            column(Comment::authorId).bigInteger()
            column(Comment::parentId).bigInteger()
            column(Comment::authorName).varchar(100)
            column(Comment::authorEmail).varchar(255)
            column(Comment::content).text().notNull()
            column(Comment::approved).boolean().notNull().default("false")
            column(Comment::createdAt).timestamp().notNull().default("CURRENT_TIMESTAMP")

            foreignKey(Comment::postId)
                .references<Post>(Post::id)
                .onDelete(CascadeType.CASCADE)

            foreignKey(Comment::authorId)
                .references<User>(User::id)
                .onDelete(CascadeType.SET_NULL)

            foreignKey(Comment::parentId)
                .references<Comment>(Comment::id)
                .onDelete(CascadeType.CASCADE)
        }

        createIndex<Comment>("idx_comments_post") {
            columns(Comment::postId)
        }
    }

    override fun down() {
        dropTable<Comment>()
    }
}
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Sempre implementar down() para rollback
override fun down() {
    dropTable<User>()
}

// Usar nomes descritivos
class V001_CreateUsersTableWithEmailIndex : Migration()

// Migrations pequenas e focadas
class V001_CreateUsersTable : Migration()
class V002_AddPhoneToUsers : Migration()
class V003_CreateIndexOnUsersEmail : Migration()
```

### [AVOID] Evitar

```kotlin
// Não modificar migrations já aplicadas
// [AVOID] Editar V001 depois de executada

// Não fazer múltiplas alterações não relacionadas
class V001_CreateUsersAndPostsAndCommentsAndTags : Migration()
// [AVOID] Muitas operações em uma migration

// Não esquecer o down()
override fun down() {
    // [AVOID] Vazio ou incompleto
}
```

## Próximos Passos

- [Executando Migrations](./03-running-migrations.md) - Executor e rollback
