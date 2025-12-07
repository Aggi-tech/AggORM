package com.aggitech.orm.migrations.generator

import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager

/**
 * Command-line interface for generating Mirror files from database schema.
 *
 * Mirrors are Kotlin objects that extend Table and provide type-safe column
 * references for queries, inserts, updates, and deletes.
 *
 * Usage via Gradle task:
 * ```bash
 * ./gradlew generateMirrors \
 *     -PjdbcUrl=jdbc:postgresql://localhost:5432/mydb \
 *     -PjdbcUser=postgres \
 *     -PjdbcPassword=secret \
 *     -PbasePackage=com.myapp.generated \
 *     -PoutputDir=src/main/kotlin/com/myapp/generated
 * ```
 *
 * Or programmatically:
 * ```kotlin
 * MirrorGeneratorCli.generate(
 *     jdbcUrl = "jdbc:postgresql://localhost:5432/mydb",
 *     username = "postgres",
 *     password = "secret",
 *     basePackage = "com.myapp.generated",
 *     outputDir = "src/main/kotlin/com/myapp/generated"
 * )
 * ```
 */
object MirrorGeneratorCli {

    /**
     * Generates Mirror files from database schema.
     *
     * @param jdbcUrl JDBC connection URL
     * @param username Database username
     * @param password Database password
     * @param basePackage Package name for generated Mirror classes
     * @param outputDir Directory where Mirror files will be created
     * @param schemaName Database schema to introspect (default: "public")
     * @return List of generated Mirror files
     */
    fun generate(
        jdbcUrl: String,
        username: String,
        password: String,
        basePackage: String,
        outputDir: String,
        schemaName: String = "public"
    ): List<GeneratedMirror> {
        println("Connecting to database...")

        val connection = DriverManager.getConnection(jdbcUrl, username, password)

        return connection.use { conn ->
            generate(
                connection = conn,
                basePackage = basePackage,
                outputDir = outputDir,
                schemaName = schemaName
            )
        }
    }

    /**
     * Generates Mirror files using an existing connection.
     *
     * @param connection Active JDBC connection
     * @param basePackage Package name for generated Mirror classes
     * @param outputDir Directory where Mirror files will be created
     * @param schemaName Database schema to introspect (default: "public")
     * @return List of generated Mirror files
     */
    fun generate(
        connection: Connection,
        basePackage: String,
        outputDir: String,
        schemaName: String = "public"
    ): List<GeneratedMirror> {
        val outputPath = Paths.get(outputDir)

        println("Introspecting schema '$schemaName'...")
        val introspector = SchemaIntrospector(connection)
        val schema = introspector.introspect(schemaName)

        println("Found ${schema.tables.size} table(s)")

        if (schema.tables.isEmpty()) {
            println("No tables found in schema '$schemaName'")
            return emptyList()
        }

        println("Generating Mirrors to: $outputPath")
        val generator = MirrorGenerator(basePackage, outputPath)
        val mirrors = generator.generate(schema)

        println()
        println("Generated ${mirrors.size} Mirror(s):")
        mirrors.forEach { mirror ->
            println("  - ${mirror.className} (table: ${mirror.tableName})")
            println("    File: ${mirror.path}")
        }
        println()

        return mirrors
    }

    /**
     * Main entry point for CLI execution.
     * Reads configuration from system properties or environment variables.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val jdbcUrl = getConfig("jdbcUrl", "AGGO_JDBC_URL")
            ?: error("Missing required config: jdbcUrl (or env AGGO_JDBC_URL)")

        val username = getConfig("jdbcUser", "AGGO_JDBC_USER")
            ?: error("Missing required config: jdbcUser (or env AGGO_JDBC_USER)")

        val password = getConfig("jdbcPassword", "AGGO_JDBC_PASSWORD")
            ?: error("Missing required config: jdbcPassword (or env AGGO_JDBC_PASSWORD)")

        val basePackage = getConfig("basePackage", "AGGO_BASE_PACKAGE")
            ?: "generated"

        val outputDir = getConfig("outputDir", "AGGO_OUTPUT_DIR")
            ?: "src/main/kotlin/generated"

        val schemaName = getConfig("schemaName", "AGGO_SCHEMA_NAME")
            ?: "public"

        generate(
            jdbcUrl = jdbcUrl,
            username = username,
            password = password,
            basePackage = basePackage,
            outputDir = outputDir,
            schemaName = schemaName
        )
    }

    private fun getConfig(propertyName: String, envName: String): String? {
        return System.getProperty(propertyName)
            ?: System.getenv(envName)
    }
}
