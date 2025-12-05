package com.aggitech.orm.migrations.generator

import java.nio.file.Paths

/**
 * Command-line interface for generating migrations.
 *
 * Usage example:
 * ```kotlin
 * MigrationGeneratorCli.createMigration(
 *     description = "CreateUsersTable",
 *     migrationOutputPath = "src/main/kotlin/db/migrations"
 * ) { builder ->
 *     builder.addOperation(
 *         description = "Create users table",
 *         upCode = """
 *             createTable("users") {
 *                 column { bigInteger("id").primaryKey().autoIncrement() }
 *                 column { varchar("name", 100).notNull() }
 *                 column { varchar("email", 255).notNull().unique() }
 *             }
 *         """.trimIndent(),
 *         downCode = """dropTable("users")"""
 *     )
 * }
 * ```
 */
object MigrationGeneratorCli {

    /**
     * Create a new migration file.
     *
     * @param description Human-readable description for the migration
     * @param migrationOutputPath Directory where migration file will be created
     * @param basePackage Package name for the migration class (default: "db.migrations")
     * @param block Lambda to build migration operations
     * @return Information about the generated migration
     */
    fun createMigration(
        description: String,
        migrationOutputPath: String,
        basePackage: String = "db.migrations",
        block: (MigrationBuilder) -> Unit
    ): GeneratedMigration {

        val generator = KotlinMigrationGenerator(basePackage)
        val outputPath = Paths.get(migrationOutputPath)

        val migration = generator.generate(
            description = description,
            outputDirectory = outputPath,
            block = block
        )

        println("Generated migration: ${migration.className}")
        println("File: ${migration.filePath}")
        println()

        return migration
    }

    /**
     * Helper to create a simple table creation migration.
     *
     * @param tableName Name of the table to create
     * @param migrationOutputPath Directory where migration file will be created
     * @param tableDefinition Kotlin code for table definition
     * @return Information about the generated migration
     */
    fun createTableMigration(
        tableName: String,
        migrationOutputPath: String,
        tableDefinition: String
    ): GeneratedMigration {
        val description = "Create${tableName.replaceFirstChar { it.uppercase() }}Table"

        return createMigration(description, migrationOutputPath) { builder ->
            builder.addOperation(
                description = "Create $tableName table",
                upCode = """
                    createTable("$tableName") {
                    $tableDefinition
                    }
                """.trimIndent(),
                downCode = """dropTable("$tableName")"""
            )
        }
    }

    /**
     * Helper to create an add column migration.
     *
     * @param tableName Name of the table
     * @param columnName Name of the column to add
     * @param columnDefinition Kotlin code for column definition
     * @param migrationOutputPath Directory where migration file will be created
     * @return Information about the generated migration
     */
    fun addColumnMigration(
        tableName: String,
        columnName: String,
        columnDefinition: String,
        migrationOutputPath: String
    ): GeneratedMigration {
        val description = "Add${columnName.replaceFirstChar { it.uppercase() }}To${tableName.replaceFirstChar { it.uppercase() }}"

        return createMigration(description, migrationOutputPath) { builder ->
            builder.addOperation(
                description = "Add $columnName to $tableName",
                upCode = """
                    addColumn("$tableName") {
                        $columnDefinition
                    }
                """.trimIndent(),
                downCode = """dropColumn("$tableName", "$columnName")"""
            )
        }
    }
}
