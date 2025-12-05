package com.aggitech.orm.migrations.generator

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Generates Kotlin migration source files.
 *
 * @param basePackage Package name for generated migration classes (default: "db.migrations")
 */
class KotlinMigrationGenerator(
    private val basePackage: String = "db.migrations"
) : MigrationGenerator {

    override fun generate(
        description: String,
        outputDirectory: Path,
        block: (MigrationBuilder) -> Unit
    ): GeneratedMigration {

        // Determine version and timestamp
        val version = determineNextVersion(outputDirectory)
        val timestamp = generateTimestamp()

        // Generate class name
        val className = "V${version.toString().padStart(3, '0')}_${timestamp}_${sanitizeDescription(description)}"

        // Build operations
        val builder = MigrationBuilderImpl()
        block(builder)

        // Generate file content
        val content = generateKotlinCode(className, builder.operations)

        // Write to file
        Files.createDirectories(outputDirectory)
        val filePath = outputDirectory.resolve("$className.kt")
        Files.writeString(filePath, content)

        return GeneratedMigration(
            version = version,
            timestamp = timestamp,
            description = description,
            className = className,
            filePath = filePath,
            content = content
        )
    }

    private fun generateKotlinCode(className: String, operations: List<Operation>): String {
        val upOperations = operations.joinToString("\n\n") { "        ${it.upCode}" }
        val downOperations = operations.reversed().joinToString("\n\n") { "        ${it.downCode}" }

        return """
            |package $basePackage
            |
            |import com.aggitech.orm.migrations.core.Migration
            |import com.aggitech.orm.migrations.core.CascadeType
            |import com.aggitech.orm.migrations.dsl.ColumnType
            |
            |/**
            | * Auto-generated migration
            | *
            | * Changes:
            |${operations.joinToString("\n") { " * - ${it.description}" }}
            | */
            |class $className : Migration() {
            |
            |    override fun up() {
            |$upOperations
            |    }
            |
            |    override fun down() {
            |$downOperations
            |    }
            |}
        """.trimMargin()
    }

    private fun determineNextVersion(outputDirectory: Path): Int {
        if (!Files.exists(outputDirectory)) return 1

        return try {
            Files.list(outputDirectory).use { stream ->
                stream
                    .filter { it.fileName.toString().startsWith("V") && it.fileName.toString().endsWith(".kt") }
                    .map {
                        val fileName = it.fileName.toString()
                        val versionStr = fileName.substringAfter("V").substringBefore("_")
                        versionStr.toIntOrNull() ?: 0
                    }
                    .max(Comparator.naturalOrder())
                    .orElse(0) + 1
            }
        } catch (e: Exception) {
            1
        }
    }

    private fun generateTimestamp(): Long {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        return LocalDateTime.now().format(formatter).toLong()
    }

    private fun sanitizeDescription(description: String): String {
        return description
            .replace(Regex("[^a-zA-Z0-9_]"), "")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private data class Operation(
        val description: String,
        val upCode: String,
        val downCode: String
    )

    private class MigrationBuilderImpl : MigrationBuilder {
        val operations = mutableListOf<Operation>()

        override fun addOperation(description: String, upCode: String, downCode: String) {
            operations.add(Operation(description, upCode, downCode))
        }
    }
}
