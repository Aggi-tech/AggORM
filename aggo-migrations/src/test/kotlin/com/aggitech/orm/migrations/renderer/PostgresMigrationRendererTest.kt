package com.aggitech.orm.migrations.renderer

import com.aggitech.orm.migrations.core.*
import com.aggitech.orm.migrations.dsl.ColumnType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostgresMigrationRendererTest {

    private val renderer = PostgresMigrationRenderer()

    @Test
    fun `should render CREATE TABLE with all features`() {
        val operation = MigrationOperation.CreateTable(
            name = "users",
            schema = "public",
            columns = listOf(
                ColumnDefinition("id", ColumnType.BigInteger, nullable = false, primaryKey = true, autoIncrement = true),
                ColumnDefinition("name", ColumnType.Varchar(100), nullable = false),
                ColumnDefinition("email", ColumnType.Varchar(255), nullable = false, unique = true),
                ColumnDefinition("created_at", ColumnType.Timestamp, nullable = false, defaultValue = "CURRENT_TIMESTAMP")
            ),
            primaryKeys = listOf("id"),
            foreignKeys = emptyList(),
            indexes = emptyList(),
            uniqueConstraints = emptyList()
        )

        val sql = renderer.render(operation)

        assertEquals(1, sql.size)
        val createTableSql = sql[0]

        assertTrue(createTableSql.contains("CREATE TABLE \"users\""), "Should contain CREATE TABLE")
        assertTrue(createTableSql.contains("\"id\" BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY"), "Should have auto-increment ID")
        assertTrue(createTableSql.contains("\"name\" VARCHAR(100) NOT NULL"), "Should have name column")
        assertTrue(createTableSql.contains("\"email\" VARCHAR(255) NOT NULL UNIQUE"), "Should have unique email")
        assertTrue(createTableSql.contains("\"created_at\" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"), "Should have timestamp with default")
        assertTrue(createTableSql.contains("PRIMARY KEY (\"id\")"), "Should have primary key constraint")
    }

    @Test
    fun `should render CREATE TABLE with foreign keys`() {
        val operation = MigrationOperation.CreateTable(
            name = "posts",
            schema = "public",
            columns = listOf(
                ColumnDefinition("id", ColumnType.BigInteger, nullable = false, autoIncrement = true),
                ColumnDefinition("user_id", ColumnType.BigInteger, nullable = false),
                ColumnDefinition("title", ColumnType.Varchar(200), nullable = false)
            ),
            primaryKeys = listOf("id"),
            foreignKeys = listOf(
                ForeignKeyDefinition(
                    columnName = "user_id",
                    referencedTable = "users",
                    referencedColumn = "id",
                    onDelete = CascadeType.CASCADE,
                    onUpdate = CascadeType.CASCADE
                )
            ),
            indexes = emptyList(),
            uniqueConstraints = emptyList()
        )

        val sql = renderer.render(operation)

        assertEquals(2, sql.size, "Should have CREATE TABLE and ADD CONSTRAINT statements")

        val createTableSql = sql[0]
        assertTrue(createTableSql.contains("CREATE TABLE \"posts\""))

        val fkSql = sql[1]
        assertTrue(fkSql.contains("ALTER TABLE \"posts\""))
        assertTrue(fkSql.contains("ADD CONSTRAINT \"fk_posts_user_id\""))
        assertTrue(fkSql.contains("FOREIGN KEY (\"user_id\")"))
        assertTrue(fkSql.contains("REFERENCES \"users\" (\"id\")"))
        assertTrue(fkSql.contains("ON DELETE CASCADE"))
        assertTrue(fkSql.contains("ON UPDATE CASCADE"))
    }

    @Test
    fun `should render CREATE TABLE with indexes`() {
        val operation = MigrationOperation.CreateTable(
            name = "products",
            schema = "public",
            columns = listOf(
                ColumnDefinition("id", ColumnType.BigInteger, nullable = false),
                ColumnDefinition("sku", ColumnType.Varchar(50), nullable = false),
                ColumnDefinition("name", ColumnType.Varchar(200), nullable = false)
            ),
            primaryKeys = listOf("id"),
            foreignKeys = emptyList(),
            indexes = listOf(
                IndexDefinition(columns = listOf("sku"), unique = true),
                IndexDefinition(columns = listOf("name"), unique = false)
            ),
            uniqueConstraints = emptyList()
        )

        val sql = renderer.render(operation)

        assertEquals(3, sql.size, "Should have CREATE TABLE and 2 CREATE INDEX statements")

        val index1Sql = sql[1]
        assertTrue(index1Sql.contains("CREATE UNIQUE INDEX"))
        assertTrue(index1Sql.contains("\"idx_products_sku\""))
        assertTrue(index1Sql.contains("ON \"products\" (\"sku\")"))

        val index2Sql = sql[2]
        assertTrue(index2Sql.contains("CREATE INDEX"))
        assertTrue(index2Sql.contains("\"idx_products_name\""))
        assertTrue(index2Sql.contains("ON \"products\" (\"name\")"))
    }

    @Test
    fun `should render DROP TABLE`() {
        val operation = MigrationOperation.DropTable(
            name = "old_table",
            schema = "public",
            ifExists = true
        )

        val sql = renderer.render(operation)

        assertEquals(1, sql.size)
        assertEquals("DROP TABLE IF EXISTS \"old_table\"", sql[0])
    }

    @Test
    fun `should render RENAME TABLE`() {
        val operation = MigrationOperation.RenameTable(
            oldName = "old_name",
            newName = "new_name",
            schema = "public"
        )

        val sql = renderer.render(operation)

        assertEquals(1, sql.size)
        assertEquals("ALTER TABLE \"old_name\" RENAME TO \"new_name\"", sql[0])
    }

    @Test
    fun `should render ADD COLUMN`() {
        val operation = MigrationOperation.AddColumn(
            tableName = "users",
            schema = "public",
            column = ColumnDefinition(
                name = "phone",
                type = ColumnType.Varchar(20),
                nullable = true
            )
        )

        val sql = renderer.render(operation)

        assertEquals(1, sql.size)
        assertEquals("ALTER TABLE \"users\" ADD COLUMN \"phone\" VARCHAR(20)", sql[0])
    }

    @Test
    fun `should render DROP COLUMN`() {
        val operation = MigrationOperation.DropColumn(
            tableName = "users",
            columnName = "old_column",
            schema = "public",
            ifExists = true
        )

        val sql = renderer.render(operation)

        assertEquals(1, sql.size)
        assertEquals("ALTER TABLE \"users\" DROP COLUMN IF EXISTS \"old_column\"", sql[0])
    }

    @Test
    fun `should render ALTER COLUMN with type change`() {
        val operation = MigrationOperation.AlterColumn(
            tableName = "users",
            columnName = "age",
            newType = ColumnType.SmallInteger,
            nullable = false,
            schema = "public"
        )

        val sql = renderer.render(operation)

        assertEquals(2, sql.size, "Should have ALTER TYPE and ALTER NOT NULL statements")
        assertEquals("ALTER TABLE \"users\" ALTER COLUMN \"age\" TYPE SMALLINT", sql[0])
        assertEquals("ALTER TABLE \"users\" ALTER COLUMN \"age\" SET NOT NULL", sql[1])
    }

    @Test
    fun `should render RENAME COLUMN`() {
        val operation = MigrationOperation.RenameColumn(
            tableName = "users",
            oldName = "old_name",
            newName = "new_name",
            schema = "public"
        )

        val sql = renderer.render(operation)

        assertEquals(1, sql.size)
        assertEquals("ALTER TABLE \"users\" RENAME COLUMN \"old_name\" TO \"new_name\"", sql[0])
    }

    @Test
    fun `should render CREATE INDEX`() {
        val operation = MigrationOperation.CreateIndex(
            tableName = "users",
            index = IndexDefinition(
                name = "idx_users_email",
                columns = listOf("email"),
                unique = true
            ),
            schema = "public"
        )

        val sql = renderer.render(operation)

        assertEquals(1, sql.size)
        assertEquals("CREATE UNIQUE INDEX \"idx_users_email\" ON \"users\" (\"email\")", sql[0])
    }

    @Test
    fun `should render DROP INDEX`() {
        val operation = MigrationOperation.DropIndex(
            indexName = "idx_old_index",
            schema = "public",
            ifExists = true
        )

        val sql = renderer.render(operation)

        assertEquals(1, sql.size)
        assertEquals("DROP INDEX IF EXISTS \"idx_old_index\"", sql[0])
    }

    @Test
    fun `should render all PostgreSQL data types correctly`() {
        val types = mapOf(
            ColumnType.Varchar(255) to "VARCHAR(255)",
            ColumnType.Char(10) to "CHAR(10)",
            ColumnType.Text to "TEXT",
            ColumnType.Integer to "INTEGER",
            ColumnType.BigInteger to "BIGINT",
            ColumnType.SmallInteger to "SMALLINT",
            ColumnType.Boolean to "BOOLEAN",
            ColumnType.Decimal(10, 2) to "DECIMAL(10, 2)",
            ColumnType.Float to "REAL",
            ColumnType.Double to "DOUBLE PRECISION",
            ColumnType.Date to "DATE",
            ColumnType.Time to "TIME",
            ColumnType.Timestamp to "TIMESTAMP",
            ColumnType.Binary(100) to "BYTEA",
            ColumnType.Blob to "BYTEA",
            ColumnType.Json to "JSON",
            ColumnType.Jsonb to "JSONB",
            ColumnType.Uuid to "UUID"
        )

        types.forEach { (type, expectedSql) ->
            val operation = MigrationOperation.AddColumn(
                tableName = "test_table",
                schema = "public",
                column = ColumnDefinition("test_column", type)
            )

            val sql = renderer.render(operation)

            assertTrue(
                sql[0].contains(expectedSql),
                "Expected type $expectedSql but got ${sql[0]}"
            )
        }
    }

    @Test
    fun `should handle schema-qualified table names`() {
        val operation = MigrationOperation.DropTable(
            name = "test_table",
            schema = "custom_schema",
            ifExists = true
        )

        val sql = renderer.render(operation)

        assertEquals(1, sql.size)
        assertEquals("DROP TABLE IF EXISTS \"custom_schema\".\"test_table\"", sql[0])
    }
}
