package com.aggitech.orm.migrations.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinMigrationGeneratorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should generate CREATE TABLE migration`() {
        val generator = KotlinMigrationGenerator()

        val migration = generator.generate(
            description = "CreateUsersTable",
            outputDirectory = tempDir
        ) { builder ->
            builder.addOperation(
                description = "Create users table",
                upCode = """
                    createTable("users") {
                        column { bigInteger("id").primaryKey().autoIncrement() }
                        column { varchar("name", 100).notNull() }
                    }
                """.trimIndent(),
                downCode = """dropTable("users")"""
            )
        }

        assertEquals(1, migration.version, "First migration should be version 1")
        assertTrue(migration.className.startsWith("V001_"))
        assertTrue(migration.className.endsWith("_CreateUsersTable"))

        // Verify file was created
        assertTrue(Files.exists(migration.filePath), "Migration file should exist")

        // Verify content
        val content = Files.readString(migration.filePath)
        assertTrue(content.contains("package db.migrations"))
        assertTrue(content.contains("class ${migration.className} : Migration()"))
        assertTrue(content.contains("override fun up()"))
        assertTrue(content.contains("override fun down()"))
        assertTrue(content.contains("createTable(\"users\")"))
        assertTrue(content.contains("dropTable(\"users\")"))
    }

    @Test
    fun `should generate ADD COLUMN migration`() {
        val generator = KotlinMigrationGenerator()

        val migration = generator.generate(
            description = "AddEmailToUsers",
            outputDirectory = tempDir
        ) { builder ->
            builder.addOperation(
                description = "Add email column",
                upCode = """
                    addColumn("users") {
                        varchar("email", 255).notNull().unique()
                    }
                """.trimIndent(),
                downCode = """dropColumn("users", "email")"""
            )
        }

        val content = Files.readString(migration.filePath)
        assertTrue(content.contains("addColumn(\"users\")"))
        assertTrue(content.contains("dropColumn(\"users\", \"email\")"))
    }

    @Test
    fun `should generate migration with multiple operations`() {
        val generator = KotlinMigrationGenerator()

        val migration = generator.generate(
            description = "ComplexMigration",
            outputDirectory = tempDir
        ) { builder ->
            builder.addOperation(
                description = "Create products table",
                upCode = """createTable("products") { /* ... */ }""",
                downCode = """dropTable("products")"""
            )
            builder.addOperation(
                description = "Add index to products",
                upCode = """createIndex("products", listOf("name"), unique = true)""",
                downCode = """dropIndex("idx_products_name")"""
            )
        }

        val content = Files.readString(migration.filePath)
        assertTrue(content.contains("Create products table"))
        assertTrue(content.contains("Add index to products"))
        assertTrue(content.contains("createTable(\"products\")"))
        assertTrue(content.contains("createIndex(\"products\""))
    }

    @Test
    fun `should generate valid Kotlin code`() {
        val generator = KotlinMigrationGenerator()

        val migration = generator.generate(
            description = "TestMigration",
            outputDirectory = tempDir
        ) { builder ->
            builder.addOperation(
                description = "Test operation",
                upCode = """executeSql("SELECT 1")""",
                downCode = """executeSql("SELECT 0")"""
            )
        }

        val content = Files.readString(migration.filePath)

        // Basic Kotlin syntax checks
        assertTrue(content.contains("package "))
        assertTrue(content.contains("import "))
        assertTrue(content.contains("class "))
        assertTrue(content.contains(": Migration()"))
        assertTrue(content.contains("override fun up()"))
        assertTrue(content.contains("override fun down()"))

        // Ensure proper indentation
        val lines = content.lines()
        val upLine = lines.find { it.contains("override fun up()") }
        val downLine = lines.find { it.contains("override fun down()") }

        assertTrue(upLine?.startsWith("    ") == true, "Methods should be indented")
        assertTrue(downLine?.startsWith("    ") == true, "Methods should be indented")
    }

    @Test
    fun `should increment version numbers correctly`() {
        val generator = KotlinMigrationGenerator()

        // Generate first migration
        val migration1 = generator.generate(
            description = "First",
            outputDirectory = tempDir
        ) { it.addOperation("Op 1", "up1", "down1") }

        assertEquals(1, migration1.version)

        // Generate second migration
        val migration2 = generator.generate(
            description = "Second",
            outputDirectory = tempDir
        ) { it.addOperation("Op 2", "up2", "down2") }

        assertEquals(2, migration2.version)

        // Generate third migration
        val migration3 = generator.generate(
            description = "Third",
            outputDirectory = tempDir
        ) { it.addOperation("Op 3", "up3", "down3") }

        assertEquals(3, migration3.version)
    }

    @Test
    fun `should sanitize description for class name`() {
        val generator = KotlinMigrationGenerator()

        val migration = generator.generate(
            description = "Add Email & Phone (New!)",
            outputDirectory = tempDir
        ) { it.addOperation("Op", "up", "down") }

        assertTrue(migration.className.contains("AddEmailPhoneNew"))
        assertFalse(migration.className.contains("&"))
        assertFalse(migration.className.contains("!"))
        assertFalse(migration.className.contains("("))
    }

    @Test
    fun `should reverse down operations`() {
        val generator = KotlinMigrationGenerator()

        val migration = generator.generate(
            description = "MultiOps",
            outputDirectory = tempDir
        ) { builder ->
            builder.addOperation("Op1", "up1", "down1")
            builder.addOperation("Op2", "up2", "down2")
            builder.addOperation("Op3", "up3", "down3")
        }

        val content = Files.readString(migration.filePath)

        // In up(), operations should be in order
        val upSection = content.substringAfter("override fun up()").substringBefore("override fun down()")
        assertTrue(upSection.indexOf("up1") < upSection.indexOf("up2"))
        assertTrue(upSection.indexOf("up2") < upSection.indexOf("up3"))

        // In down(), operations should be reversed
        val downSection = content.substringAfter("override fun down()")
        assertTrue(downSection.indexOf("down3") < downSection.indexOf("down2"))
        assertTrue(downSection.indexOf("down2") < downSection.indexOf("down1"))
    }

    @Test
    fun `should use custom base package`() {
        val generator = KotlinMigrationGenerator(basePackage = "com.example.migrations")

        val migration = generator.generate(
            description = "Test",
            outputDirectory = tempDir
        ) { it.addOperation("Op", "up", "down") }

        val content = Files.readString(migration.filePath)
        assertTrue(content.contains("package com.example.migrations"))
    }
}
