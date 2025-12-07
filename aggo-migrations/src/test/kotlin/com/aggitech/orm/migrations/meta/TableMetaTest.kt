package com.aggitech.orm.migrations.meta

import com.aggitech.orm.migrations.dsl.ColumnType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes para TableMeta e ColumnMeta
 */
class TableMetaTest {

    // Exemplo de TableMeta como seria gerado
    object UsersTable : TableMeta("users") {
        val ID = uuid("id").primaryKey()
        val NAME = varchar("name", 100).notNull()
        val EMAIL = varchar("email", 255).notNull().unique()
        val AGE = integer("age").nullable()
        val CREATED_AT = timestamp("created_at").notNull().default("CURRENT_TIMESTAMP")
        val PROFILE_ID = uuid("profile_id").references("profiles", "id", "CASCADE", null)
    }

    @Test
    fun `should create TableMeta with correct table name`() {
        assertEquals("users", UsersTable.tableName)
        assertEquals("public", UsersTable.schema)
    }

    @Test
    fun `should register all columns`() {
        val columns = UsersTable.columns
        assertEquals(6, columns.size)

        val columnNames = columns.map { it.name }
        assertTrue("id" in columnNames)
        assertTrue("name" in columnNames)
        assertTrue("email" in columnNames)
        assertTrue("age" in columnNames)
        assertTrue("created_at" in columnNames)
        assertTrue("profile_id" in columnNames)
    }

    @Test
    fun `should find column by name`() {
        val idColumn = UsersTable.findColumn("id")
        assertNotNull(idColumn)
        assertEquals("id", idColumn.name)
        assertTrue(idColumn.type is ColumnType.Uuid)

        val nonExistent = UsersTable.findColumn("non_existent")
        assertNull(nonExistent)
    }

    @Test
    fun `should identify primary key column`() {
        val pk = UsersTable.primaryKeyColumn()
        assertNotNull(pk)
        assertEquals("id", pk.name)
        assertTrue(pk.primaryKey)
    }

    @Test
    fun `should identify foreign key columns`() {
        val fks = UsersTable.foreignKeyColumns()
        assertEquals(1, fks.size)

        val profileFk = fks.first()
        assertEquals("profile_id", profileFk.name)
        assertNotNull(profileFk.references)
        assertEquals("profiles", profileFk.references!!.table)
        assertEquals("id", profileFk.references!!.column)
        assertEquals("CASCADE", profileFk.references!!.onDelete)
    }

    @Test
    fun `should apply column modifiers correctly`() {
        // ID: primaryKey
        val id = UsersTable.ID
        assertTrue(id.primaryKey)
        assertTrue(id.nullable) // PK nao implica notNull automaticamente

        // NAME: notNull
        val name = UsersTable.NAME
        assertEquals(false, name.nullable)
        assertEquals(false, name.unique)

        // EMAIL: notNull + unique
        val email = UsersTable.EMAIL
        assertEquals(false, email.nullable)
        assertTrue(email.unique)

        // AGE: nullable (explicit)
        val age = UsersTable.AGE
        assertTrue(age.nullable)

        // CREATED_AT: notNull + default
        val createdAt = UsersTable.CREATED_AT
        assertEquals(false, createdAt.nullable)
        assertEquals("CURRENT_TIMESTAMP", createdAt.defaultValue)
    }

    @Test
    fun `should handle different column types`() {
        assertEquals(ColumnType.Uuid, UsersTable.ID.type)
        assertEquals(ColumnType.Varchar(100), UsersTable.NAME.type)
        assertEquals(ColumnType.Varchar(255), UsersTable.EMAIL.type)
        assertEquals(ColumnType.Integer, UsersTable.AGE.type)
        assertEquals(ColumnType.Timestamp, UsersTable.CREATED_AT.type)
    }

    @Test
    fun `should set tableName on columns`() {
        // tableName e preenchido pelo TableMeta.registerColumn
        assertEquals("users", UsersTable.ID.tableName)
        assertEquals("users", UsersTable.NAME.tableName)
        assertEquals("users", UsersTable.EMAIL.tableName)
    }
}

/**
 * Testes para ColumnMeta isolado
 */
class ColumnMetaTest {

    @Test
    fun `should create ColumnMeta with default values`() {
        val col = ColumnMeta("test", ColumnType.Varchar(100))

        assertEquals("test", col.name)
        assertEquals(ColumnType.Varchar(100), col.type)
        assertTrue(col.nullable)
        assertEquals(false, col.primaryKey)
        assertEquals(false, col.unique)
        assertEquals(false, col.autoIncrement)
        assertNull(col.defaultValue)
        assertNull(col.references)
    }

    @Test
    fun `should chain modifiers correctly`() {
        val col = ColumnMeta("email", ColumnType.Varchar(255))
            .notNull()
            .unique()
            .default("''")

        assertEquals("email", col.name)
        assertEquals(false, col.nullable)
        assertTrue(col.unique)
        assertEquals("''", col.defaultValue)
    }

    @Test
    fun `should add foreign key reference`() {
        val col = ColumnMeta("user_id", ColumnType.Uuid)
            .references("users", "id", "CASCADE", "NO_ACTION")

        assertNotNull(col.references)
        assertEquals("users", col.references!!.table)
        assertEquals("id", col.references!!.column)
        assertEquals("CASCADE", col.references!!.onDelete)
        assertEquals("NO_ACTION", col.references!!.onUpdate)
    }
}

/**
 * Testes para ColumnMetaBuilder
 */
class ColumnMetaBuilderTest {

    @Test
    fun `should build ColumnMeta with builder`() {
        val col = ColumnMetaBuilder("id", ColumnType.Uuid)
            .primaryKey()
            .notNull()
            .build()

        assertEquals("id", col.name)
        assertEquals(ColumnType.Uuid, col.type)
        assertTrue(col.primaryKey)
        assertEquals(false, col.nullable)
    }

    @Test
    fun `should build ColumnMeta with all modifiers`() {
        val col = ColumnMetaBuilder("code", ColumnType.Varchar(10))
            .unique()
            .notNull()
            .default("'DEFAULT'")
            .build()

        assertEquals("code", col.name)
        assertTrue(col.unique)
        assertEquals(false, col.nullable)
        assertEquals("'DEFAULT'", col.defaultValue)
    }

    @Test
    fun `should build ColumnMeta with foreign key`() {
        val col = ColumnMetaBuilder("category_id", ColumnType.Integer)
            .notNull()
            .references("categories", "id", "SET_NULL")
            .build()

        assertNotNull(col.references)
        assertEquals("categories", col.references!!.table)
        assertEquals("id", col.references!!.column)
        assertEquals("SET_NULL", col.references!!.onDelete)
    }
}
