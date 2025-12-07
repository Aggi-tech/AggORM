package com.aggitech.orm.migrations.dsl

import com.aggitech.orm.migrations.core.MigrationOperation
import com.aggitech.orm.migrations.meta.ColumnMeta
import com.aggitech.orm.migrations.meta.TableMeta
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes para AggORMTableBuilder
 */
class AggORMTableBuilderTest {

    // TableMeta de exemplo para testes
    object UsersTable : TableMeta("users") {
        val ID = uuid("id").primaryKey()
        val NAME = varchar("name", 100).notNull()
        val EMAIL = varchar("email", 255).notNull().unique()
        val OLD_FIELD = varchar("old_field", 50)
        val PHONE = varchar("phone", 20).nullable()
    }

    // ==================== Testes com ColumnMeta ====================

    @Test
    fun `should add column using ColumnMeta`() {
        val builder = AggORMTableBuilder("users")
        builder.add(UsersTable.PHONE)

        val operations = builder.build()
        assertEquals(1, operations.size)

        val addOp = operations.first() as MigrationOperation.AddColumn
        assertEquals("users", addOp.tableName)
        assertEquals("phone", addOp.column.name)
        assertEquals(ColumnType.Varchar(20), addOp.column.type)
    }

    @Test
    fun `should drop column using ColumnMeta`() {
        val builder = AggORMTableBuilder("users")
        builder.drop(UsersTable.OLD_FIELD)

        val operations = builder.build()
        assertEquals(1, operations.size)

        val dropOp = operations.first() as MigrationOperation.DropColumn
        assertEquals("users", dropOp.tableName)
        assertEquals("old_field", dropOp.columnName)
    }

    @Test
    fun `should create index using ColumnMeta`() {
        val builder = AggORMTableBuilder("users")
        builder.index(UsersTable.EMAIL, unique = true, name = "idx_users_email")

        val operations = builder.build()
        assertEquals(1, operations.size)

        val indexOp = operations.first() as MigrationOperation.CreateIndex
        assertEquals("users", indexOp.tableName)
        assertEquals(listOf("email"), indexOp.index.columns)
        assertTrue(indexOp.index.unique)
        assertEquals("idx_users_email", indexOp.index.name)
    }

    // ==================== Testes com Strings ====================

    @Test
    fun `should add column using string`() {
        val builder = AggORMTableBuilder("users")
        builder.addColumn("phone") {
            varchar(20).nullable()
        }

        val operations = builder.build()
        assertEquals(1, operations.size)

        val addOp = operations.first() as MigrationOperation.AddColumn
        assertEquals("users", addOp.tableName)
        assertEquals("phone", addOp.column.name)
        assertEquals(ColumnType.Varchar(20), addOp.column.type)
        assertTrue(addOp.column.nullable)
    }

    @Test
    fun `should drop column using string`() {
        val builder = AggORMTableBuilder("users")
        builder.dropColumn("old_field")

        val operations = builder.build()
        assertEquals(1, operations.size)

        val dropOp = operations.first() as MigrationOperation.DropColumn
        assertEquals("users", dropOp.tableName)
        assertEquals("old_field", dropOp.columnName)
    }

    @Test
    fun `should rename column using strings`() {
        val builder = AggORMTableBuilder("users")
        builder.renameColumn("old_name", "new_name")

        val operations = builder.build()
        assertEquals(1, operations.size)

        val renameOp = operations.first() as MigrationOperation.RenameColumn
        assertEquals("users", renameOp.tableName)
        assertEquals("old_name", renameOp.oldName)
        assertEquals("new_name", renameOp.newName)
    }

    @Test
    fun `should create index using strings`() {
        val builder = AggORMTableBuilder("users")
        builder.index("email", "name", unique = false, name = "idx_users_email_name")

        val operations = builder.build()
        assertEquals(1, operations.size)

        val indexOp = operations.first() as MigrationOperation.CreateIndex
        assertEquals("users", indexOp.tableName)
        assertEquals(listOf("email", "name"), indexOp.index.columns)
        assertEquals(false, indexOp.index.unique)
    }

    @Test
    fun `should drop index`() {
        val builder = AggORMTableBuilder("users")
        builder.dropIndex("idx_users_email")

        val operations = builder.build()
        assertEquals(1, operations.size)

        val dropOp = operations.first() as MigrationOperation.DropIndex
        assertEquals("idx_users_email", dropOp.indexName)
    }

    @Test
    fun `should create foreign key using strings`() {
        val builder = AggORMTableBuilder("posts")
        builder.foreign("user_id", "users", "id")

        val operations = builder.build()
        assertEquals(1, operations.size)

        val fkOp = operations.first() as MigrationOperation.AddForeignKey
        assertEquals("posts", fkOp.tableName)
        assertEquals("user_id", fkOp.foreignKey.columnName)
        assertEquals("users", fkOp.foreignKey.referencedTable)
        assertEquals("id", fkOp.foreignKey.referencedColumn)
    }

    // ==================== Testes com multiplas operacoes ====================

    @Test
    fun `should handle multiple operations`() {
        val builder = AggORMTableBuilder("users")
        builder.add(UsersTable.PHONE)
        builder.drop(UsersTable.OLD_FIELD)
        builder.index(UsersTable.EMAIL, unique = true)

        val operations = builder.build()
        assertEquals(3, operations.size)

        assertTrue(operations[0] is MigrationOperation.AddColumn)
        assertTrue(operations[1] is MigrationOperation.DropColumn)
        assertTrue(operations[2] is MigrationOperation.CreateIndex)
    }

    // ==================== Testes do ColumnBuilder interno ====================

    @Test
    fun `should build column with all types`() {
        val builder = AggORMTableBuilder("test")

        builder.addColumn("col_varchar") { varchar(100) }
        builder.addColumn("col_int") { integer() }
        builder.addColumn("col_bigint") { bigint() }
        builder.addColumn("col_bool") { boolean() }
        builder.addColumn("col_ts") { timestamp() }
        builder.addColumn("col_uuid") { uuid() }
        builder.addColumn("col_json") { json() }
        builder.addColumn("col_decimal") { decimal(10, 2) }

        val operations = builder.build()
        assertEquals(8, operations.size)

        val types = operations.map { (it as MigrationOperation.AddColumn).column.type }
        assertTrue(types[0] is ColumnType.Varchar)
        assertEquals(ColumnType.Integer, types[1])
        assertEquals(ColumnType.BigInteger, types[2])
        assertEquals(ColumnType.Boolean, types[3])
        assertEquals(ColumnType.Timestamp, types[4])
        assertEquals(ColumnType.Uuid, types[5])
        assertEquals(ColumnType.Json, types[6])
        assertTrue(types[7] is ColumnType.Decimal)
    }

    @Test
    fun `should apply column modifiers in builder`() {
        val builder = AggORMTableBuilder("test")
        builder.addColumn("email") {
            varchar(255).notNull().unique().default("''")
        }

        val operations = builder.build()
        val column = (operations.first() as MigrationOperation.AddColumn).column

        assertEquals(false, column.nullable)
        assertTrue(column.unique)
        assertEquals("''", column.defaultValue)
    }
}
