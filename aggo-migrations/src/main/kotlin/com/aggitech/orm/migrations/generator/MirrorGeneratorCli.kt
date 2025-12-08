package com.aggitech.orm.migrations.generator

import com.aggitech.orm.config.AggoPropertiesLoader
import com.aggitech.orm.config.MirrorConfig
import com.aggitech.orm.jdbc.JdbcConnectionManager
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
 * ## Automatic Configuration (Recommended)
 *
 * Simply configure in your `application.yml` or `application.properties`:
 *
 * ```yaml
 * aggo:
 *   orm:
 *     database: mydb
 *     host: localhost
 *     username: user
 *     password: pass
 *     mirrors:
 *       base-package: com.myapp.generated.mirrors
 *       output-dir: src/main/kotlin
 *       schema-name: public
 * ```
 *
 * Then run without arguments:
 * ```bash
 * ./gradlew generateMirrors
 * ```
 *
 * Or programmatically:
 * ```kotlin
 * MirrorGeneratorCli.generate()  // Uses configuration from application.yml
 * ```
 *
 * ## Manual Configuration
 *
 * You can also pass parameters explicitly:
 * ```bash
 * ./gradlew generateMirrors \
 *     -PjdbcUrl=jdbc:postgresql://localhost:5432/mydb \
 *     -PjdbcUser=postgres \
 *     -PjdbcPassword=secret \
 *     -PbasePackage=com.myapp.generated \
 *     -PoutputDir=src/main/kotlin/com/myapp/generated
 * ```
 */
object MirrorGeneratorCli {

    /**
     * Generates Mirror files using automatic configuration from application.yml/properties.
     *
     * Configuration is loaded from:
     * 1. application.yml / application.yaml
     * 2. application.properties
     * 3. Environment variables
     * 4. System properties
     *
     * @return List of generated Mirror files
     * @throws IllegalStateException if database configuration is not found
     */
    fun generate(): List<GeneratedMirror> {
        // Carrega configuração automaticamente
        val fullConfig = AggoPropertiesLoader.loadFullConfigFromClasspath()
            ?: throw IllegalStateException(
                "Database configuration not found. " +
                "Please configure aggo.orm.* properties in application.yml or application.properties"
            )

        val dbConfig = fullConfig.dbConfig
        val mirrorConfig = fullConfig.mirrorConfig

        println("Using configuration from application.yml/properties:")
        println("  Database: ${dbConfig.database}@${dbConfig.host}:${dbConfig.port}")
        println("  Mirror package: ${mirrorConfig.basePackage}")
        println("  Output directory: ${mirrorConfig.outputDir}")
        println("  Schema: ${mirrorConfig.schemaName}")
        println()

        // Usa o JdbcConnectionManager para obter conexão
        if (!JdbcConnectionManager.isInitialized()) {
            JdbcConnectionManager.register("default", dbConfig)
        }

        return JdbcConnectionManager.withConnection { connection ->
            generate(
                connection = connection,
                basePackage = mirrorConfig.basePackage,
                outputDir = mirrorConfig.outputDir,
                schemaName = mirrorConfig.schemaName
            )
        }
    }

    /**
     * Generates Mirror files from database schema with explicit parameters.
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
        val outputPath = resolveOutputPath(basePackage, outputDir)

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
     * Resolves the full output path including package directory structure.
     */
    private fun resolveOutputPath(basePackage: String, outputDir: String): Path {
        val basePath = Paths.get(outputDir)
        val packagePath = basePackage.replace('.', '/')
        return basePath.resolve(packagePath)
    }

    /**
     * Main entry point for CLI execution.
     *
     * First tries to load configuration from application.yml/properties.
     * Falls back to system properties and environment variables if not found.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        // Tenta carregar configuração automaticamente primeiro
        val fullConfig = AggoPropertiesLoader.loadFullConfigFromClasspath()

        if (fullConfig != null) {
            // Usa configuração automática
            generate()
        } else {
            // Fallback: usa system properties / env vars
            val jdbcUrl = getConfig("jdbcUrl", "AGGO_JDBC_URL")
                ?: error(
                    "Database configuration not found.\n" +
                    "Please either:\n" +
                    "  1. Configure aggo.orm.* properties in application.yml or application.properties\n" +
                    "  2. Pass -PjdbcUrl=... -PjdbcUser=... -PjdbcPassword=... as Gradle properties\n" +
                    "  3. Set AGGO_JDBC_URL, AGGO_JDBC_USER, AGGO_JDBC_PASSWORD environment variables"
                )

            val username = getConfig("jdbcUser", "AGGO_JDBC_USER")
                ?: error("Missing required config: jdbcUser (or env AGGO_JDBC_USER)")

            val password = getConfig("jdbcPassword", "AGGO_JDBC_PASSWORD")
                ?: error("Missing required config: jdbcPassword (or env AGGO_JDBC_PASSWORD)")

            val basePackage = getConfig("basePackage", "AGGO_BASE_PACKAGE")
                ?: "generated.mirrors"

            val outputDir = getConfig("outputDir", "AGGO_OUTPUT_DIR")
                ?: "src/main/kotlin"

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
    }

    private fun getConfig(propertyName: String, envName: String): String? {
        return System.getProperty(propertyName)
            ?: System.getenv(envName)
    }
}
