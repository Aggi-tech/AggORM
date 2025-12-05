package com.aggitech.orm.migrations.generator

import java.nio.file.Path

/**
 * Interface for generating migration source code files.
 * Implementations generate Kotlin migration classes from schema changes.
 */
interface MigrationGenerator {

    /**
     * Generate a migration file from detected schema changes.
     *
     * @param changes List of schema changes detected (currently not implemented - placeholder for future integration)
     * @param description Human-readable description of the migration
     * @param outputDirectory Directory where the migration file will be created
     * @return Information about the generated migration file
     */
    fun generate(
        description: String,
        outputDirectory: Path,
        block: (MigrationBuilder) -> Unit
    ): GeneratedMigration
}

/**
 * Information about a generated migration file.
 */
data class GeneratedMigration(
    val version: Int,
    val timestamp: Long,
    val description: String,
    val className: String,
    val filePath: Path,
    val content: String
)

/**
 * Builder for constructing migration operations during generation.
 */
interface MigrationBuilder {
    fun addOperation(description: String, upCode: String, downCode: String)
}
