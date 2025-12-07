package com.aggitech.orm.migrations.executor

import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.migrations.core.Migration
import com.aggitech.orm.migrations.core.MigrationException
import com.aggitech.orm.migrations.generator.SchemaIntrospector
import com.aggitech.orm.migrations.generator.TableMetaGenerator
import com.aggitech.orm.migrations.history.JdbcMigrationHistoryRepository
import com.aggitech.orm.migrations.history.MigrationRecord
import com.aggitech.orm.migrations.history.MigrationStatus
import com.aggitech.orm.migrations.renderer.MigrationRendererFactory
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import kotlin.system.measureTimeMillis

/**
 * JDBC-based implementation of MigrationExecutor.
 * Executes migrations within database transactions with full rollback support.
 *
 * @param connection JDBC connection to use for migrations
 * @param dialect SQL dialect for the target database
 * @param historyRepository Repository for tracking migration history
 */
class JdbcMigrationExecutor(
    private val connection: Connection,
    private val dialect: SqlDialect,
    private val historyRepository: JdbcMigrationHistoryRepository
) : MigrationExecutor {

    private val renderer = MigrationRendererFactory.createRenderer(dialect)

    /**
     * Configuracao para geracao automatica de TableMeta.
     * Se configurado, apos cada migration bem-sucedida os arquivos TableMeta serao regenerados.
     */
    var tableMetaConfig: TableMetaConfig? = null

    init {
        // Ensure history table exists
        historyRepository.ensureHistoryTableExists()
    }

    /**
     * Configura a geracao automatica de TableMeta.
     *
     * @param basePackage Package dos arquivos gerados (ex: "com.myapp.generated.tables")
     * @param outputDir Diretorio de saida (ex: Path.of("src/generated/tables"))
     * @param schemaName Nome do schema (default: "public")
     */
    fun enableTableMetaGeneration(
        basePackage: String,
        outputDir: Path,
        schemaName: String = "public"
    ) {
        tableMetaConfig = TableMetaConfig(basePackage, outputDir, schemaName)
    }

    override fun migrate(migrations: List<Migration>): MigrationResult {
        val executed = mutableListOf<ExecutedMigration>()
        val failed = mutableListOf<FailedMigration>()
        val skipped = mutableListOf<SkippedMigration>()

        // Sort migrations by version
        val sortedMigrations = migrations.sortedBy { it.version }

        for (migration in sortedMigrations) {
            // Check if already applied
            if (historyRepository.isApplied(migration.version)) {
                // Validate checksum
                val checksum = migration.checksum()
                if (!historyRepository.validateChecksum(migration.version, checksum)) {
                    throw MigrationException(
                        "Checksum mismatch for migration V${migration.version} (${migration.description}). " +
                                "The migration file has been modified after being applied. " +
                                "This is not allowed. Please create a new migration instead."
                    )
                }

                skipped.add(
                    SkippedMigration(
                        version = migration.version,
                        description = migration.description,
                        reason = "Already applied"
                    )
                )
                continue
            }

            // Execute migration
            try {
                val executionTime = executeMigration(migration)

                executed.add(
                    ExecutedMigration(
                        version = migration.version,
                        description = migration.description,
                        executionTimeMs = executionTime
                    )
                )
            } catch (e: Exception) {
                failed.add(
                    FailedMigration(
                        version = migration.version,
                        description = migration.description,
                        error = e
                    )
                )

                // Stop on first failure
                break
            }
        }

        val result = MigrationResult(executed, failed, skipped)

        // Regenera TableMeta se configurado
        if (tableMetaConfig != null) {
            // Gera se houve novas migrations OU se os arquivos não existem ainda
            val shouldGenerate = executed.isNotEmpty() || !tableMetaFilesExist()
            if (shouldGenerate) {
                regenerateTableMeta()
            }
        }

        return result
    }

    /**
     * Verifica se os arquivos TableMeta já existem
     */
    private fun tableMetaFilesExist(): Boolean {
        val config = tableMetaConfig ?: return true
        return config.outputDir.toFile().exists() &&
               config.outputDir.toFile().listFiles()?.isNotEmpty() == true
    }

    /**
     * Regenera os arquivos TableMeta baseado no schema atual do banco.
     */
    fun regenerateTableMeta() {
        val config = tableMetaConfig ?: return

        try {
            val introspector = SchemaIntrospector(connection)
            val schema = introspector.introspect(config.schemaName)

            val generator = TableMetaGenerator(config.basePackage, config.outputDir)
            val generatedFiles = generator.generate(schema)

            // Log opcional
            generatedFiles.forEach { file ->
                println("[AggORM] Generated TableMeta: ${file.className} -> ${file.path}")
            }
        } catch (e: Exception) {
            // Nao falha a migration por erro na geracao de TableMeta
            System.err.println("[AggORM] Warning: Failed to generate TableMeta: ${e.message}")
        }
    }

    private fun executeMigration(migration: Migration): Long {
        val originalAutoCommit = connection.autoCommit
        var executionTime = 0L

        try {
            // Start transaction
            connection.autoCommit = false

            executionTime = measureTimeMillis {
                // Execute up() to populate operations
                migration.operations.clear()
                migration.up()

                // Render and execute each operation
                for (operation in migration.operations) {
                    val sqlStatements = renderer.render(operation)

                    for (sql in sqlStatements) {
                        connection.createStatement().use { stmt ->
                            stmt.execute(sql)
                        }
                    }
                }
            }

            // Record in history
            val record = MigrationRecord(
                version = migration.version,
                timestamp = migration.timestamp,
                description = migration.description,
                checksum = migration.checksum(),
                executedAt = Instant.now(),
                executionTimeMs = executionTime,
                status = MigrationStatus.SUCCESS,
                className = migration::class.qualifiedName
            )

            historyRepository.save(record)

            // Commit transaction
            connection.commit()

        } catch (e: Exception) {
            // Rollback on error
            try {
                connection.rollback()
            } catch (rollbackException: Exception) {
                e.addSuppressed(rollbackException)
            }

            // Record failed migration
            val record = MigrationRecord(
                version = migration.version,
                timestamp = migration.timestamp,
                description = migration.description,
                checksum = migration.checksum(),
                executedAt = Instant.now(),
                executionTimeMs = executionTime,
                status = MigrationStatus.FAILED,
                errorMessage = e.message,
                className = migration::class.qualifiedName
            )

            try {
                historyRepository.save(record)
                connection.commit()
            } catch (saveException: Exception) {
                // If we can't save the failure, rollback
                try {
                    connection.rollback()
                } catch (rollbackException: Exception) {
                    saveException.addSuppressed(rollbackException)
                }
                e.addSuppressed(saveException)
            }

            throw MigrationException(
                "Failed to execute migration V${migration.version} (${migration.description}): ${e.message}",
                e
            )

        } finally {
            connection.autoCommit = originalAutoCommit
        }

        return executionTime
    }

    override fun rollback(steps: Int): MigrationResult {
        val executed = mutableListOf<ExecutedMigration>()
        val failed = mutableListOf<FailedMigration>()

        // Get last N applied migrations
        val appliedMigrations = historyRepository.findAll()
            .filter { it.status == MigrationStatus.SUCCESS }
            .sortedByDescending { it.version }
            .take(steps)

        for (record in appliedMigrations) {
            // Find the migration class
            val migration = try {
                instantiateMigration(record.className)
            } catch (e: Exception) {
                failed.add(
                    FailedMigration(
                        version = record.version,
                        description = record.description,
                        error = MigrationException(
                            "Cannot rollback migration V${record.version}: " +
                                    "Migration class ${record.className} not found or cannot be instantiated. " +
                                    "Rollback requires the migration source files.",
                            e
                        )
                    )
                )
                break
            }

            try {
                val executionTime = executeRollback(migration, record)

                executed.add(
                    ExecutedMigration(
                        version = record.version,
                        description = record.description,
                        executionTimeMs = executionTime
                    )
                )
            } catch (e: Exception) {
                failed.add(
                    FailedMigration(
                        version = record.version,
                        description = record.description,
                        error = e
                    )
                )
                break
            }
        }

        return MigrationResult(executed, failed, emptyList())
    }

    private fun executeRollback(migration: Migration, record: MigrationRecord): Long {
        val originalAutoCommit = connection.autoCommit
        var executionTime = 0L

        try {
            connection.autoCommit = false

            executionTime = measureTimeMillis {
                // Execute down() to populate rollback operations
                migration.operations.clear()
                migration.down()

                // Render and execute each operation
                for (operation in migration.operations) {
                    val sqlStatements = renderer.render(operation)

                    for (sql in sqlStatements) {
                        connection.createStatement().use { stmt ->
                            stmt.execute(sql)
                        }
                    }
                }
            }

            // Update history record status
            val updatedRecord = record.copy(
                status = MigrationStatus.ROLLED_BACK,
                executionTimeMs = executionTime
            )
            historyRepository.save(updatedRecord)

            // Commit transaction
            connection.commit()

        } catch (e: Exception) {
            // Rollback on error
            try {
                connection.rollback()
            } catch (rollbackException: Exception) {
                e.addSuppressed(rollbackException)
            }

            throw MigrationException(
                "Failed to rollback migration V${migration.version} (${migration.description}): ${e.message}",
                e
            )

        } finally {
            connection.autoCommit = originalAutoCommit
        }

        return executionTime
    }

    override fun status(migrations: List<Migration>): MigrationStatusReport {
        val appliedRecords = historyRepository.findAll()
            .filter { it.status == MigrationStatus.SUCCESS }

        val appliedVersions = appliedRecords.map { it.version }.toSet()

        val appliedMigrations = appliedRecords.map {
            AppliedMigrationInfo(
                version = it.version,
                description = it.description,
                executedAt = it.executedAt,
                executionTimeMs = it.executionTimeMs
            )
        }

        val pendingMigrations = migrations
            .filter { it.version !in appliedVersions }
            .sortedBy { it.version }
            .map {
                PendingMigrationInfo(
                    version = it.version,
                    description = it.description
                )
            }

        return MigrationStatusReport(
            appliedCount = appliedMigrations.size,
            pendingCount = pendingMigrations.size,
            appliedMigrations = appliedMigrations,
            pendingMigrations = pendingMigrations
        )
    }

    override fun validate(migrations: List<Migration>): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        val appliedRecords = historyRepository.findAll()
        val migrationsByVersion = migrations.associateBy { it.version }

        for (record in appliedRecords) {
            when (record.status) {
                MigrationStatus.FAILED -> {
                    issues.add(
                        ValidationIssue.FailedMigration(
                            version = record.version,
                            description = record.description,
                            errorMessage = record.errorMessage
                        )
                    )
                }

                MigrationStatus.SUCCESS -> {
                    val migration = migrationsByVersion[record.version]

                    if (migration == null) {
                        issues.add(
                            ValidationIssue.MissingMigration(
                                version = record.version,
                                description = record.description
                            )
                        )
                    } else {
                        val currentChecksum = migration.checksum()
                        if (currentChecksum != record.checksum) {
                            issues.add(
                                ValidationIssue.ChecksumMismatch(
                                    version = record.version,
                                    description = record.description,
                                    expected = record.checksum,
                                    actual = currentChecksum
                                )
                            )
                        }
                    }
                }

                MigrationStatus.ROLLED_BACK -> {
                    // Rolled back migrations are not an issue
                }
            }
        }

        return ValidationResult(
            valid = issues.isEmpty(),
            issues = issues
        )
    }

    private fun instantiateMigration(className: String?): Migration {
        if (className == null) {
            throw MigrationException("Migration class name not recorded in history")
        }

        try {
            val clazz = Class.forName(className)
            return clazz.getDeclaredConstructor().newInstance() as Migration
        } catch (e: Exception) {
            throw MigrationException("Cannot instantiate migration class: $className", e)
        }
    }
}

/**
 * Configuracao para geracao automatica de TableMeta.
 *
 * @param basePackage Package dos arquivos gerados (ex: "com.myapp.generated.tables")
 * @param outputDir Diretorio de saida (ex: Path.of("src/generated/tables"))
 * @param schemaName Nome do schema do banco (default: "public")
 */
data class TableMetaConfig(
    val basePackage: String,
    val outputDir: Path,
    val schemaName: String = "public"
)
