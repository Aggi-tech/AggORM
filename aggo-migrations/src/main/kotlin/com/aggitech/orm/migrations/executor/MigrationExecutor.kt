package com.aggitech.orm.migrations.executor

import com.aggitech.orm.migrations.core.Migration
import java.time.Instant

/**
 * Interface for executing database migrations.
 */
interface MigrationExecutor {

    /**
     * Apply all pending migrations in order.
     *
     * @param migrations List of available migrations (will be sorted by version)
     * @return Result of the migration execution
     */
    fun migrate(migrations: List<Migration>): MigrationResult

    /**
     * Rollback the last N migrations.
     *
     * @param steps Number of migrations to rollback (default: 1)
     * @return Result of the rollback operation
     */
    fun rollback(steps: Int = 1): MigrationResult

    /**
     * Get the current migration status.
     *
     * @param migrations List of available migrations
     * @return Status report showing applied and pending migrations
     */
    fun status(migrations: List<Migration>): MigrationStatusReport

    /**
     * Validate that all applied migrations match their recorded checksums.
     *
     * @param migrations List of available migrations
     * @return Validation result with any detected issues
     */
    fun validate(migrations: List<Migration>): ValidationResult
}

/**
 * Result of a migration execution.
 */
data class MigrationResult(
    val executed: List<ExecutedMigration>,
    val failed: List<FailedMigration>,
    val skipped: List<SkippedMigration>
) {
    val success: Boolean get() = failed.isEmpty()
    val totalExecuted: Int get() = executed.size
    val totalFailed: Int get() = failed.size
    val totalSkipped: Int get() = skipped.size
}

/**
 * Information about a successfully executed migration.
 */
data class ExecutedMigration(
    val version: Int,
    val description: String,
    val executionTimeMs: Long
)

/**
 * Information about a failed migration.
 */
data class FailedMigration(
    val version: Int,
    val description: String,
    val error: Throwable
)

/**
 * Information about a skipped migration.
 */
data class SkippedMigration(
    val version: Int,
    val description: String,
    val reason: String
)

/**
 * Report of migration status.
 */
data class MigrationStatusReport(
    val appliedCount: Int,
    val pendingCount: Int,
    val appliedMigrations: List<AppliedMigrationInfo>,
    val pendingMigrations: List<PendingMigrationInfo>
)

/**
 * Information about an applied migration.
 */
data class AppliedMigrationInfo(
    val version: Int,
    val description: String,
    val executedAt: Instant,
    val executionTimeMs: Long
)

/**
 * Information about a pending migration.
 */
data class PendingMigrationInfo(
    val version: Int,
    val description: String
)

/**
 * Result of migration validation.
 */
data class ValidationResult(
    val valid: Boolean,
    val issues: List<ValidationIssue>
)

/**
 * Issues found during validation.
 */
sealed class ValidationIssue {
    /**
     * A migration file was modified after being applied.
     */
    data class ChecksumMismatch(
        val version: Int,
        val description: String,
        val expected: String,
        val actual: String
    ) : ValidationIssue()

    /**
     * A migration was applied but its source file is missing.
     */
    data class MissingMigration(
        val version: Int,
        val description: String
    ) : ValidationIssue()

    /**
     * An applied migration has failed status in history.
     */
    data class FailedMigration(
        val version: Int,
        val description: String,
        val errorMessage: String?
    ) : ValidationIssue()
}
