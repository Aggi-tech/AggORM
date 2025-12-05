package com.aggitech.orm.migrations.history

import java.time.Instant

/**
 * Represents a single migration history record in the database.
 * Tracks the execution state and metadata of applied migrations.
 */
data class MigrationRecord(
    val id: Long? = null,
    val version: Int,
    val timestamp: Long,
    val description: String,
    val checksum: String,
    val executedAt: Instant,
    val executionTimeMs: Long,
    val status: MigrationStatus,
    val errorMessage: String? = null,
    val appliedBy: String = "aggo-migrations",
    val className: String? = null
)

/**
 * Status of a migration execution.
 */
enum class MigrationStatus {
    SUCCESS,
    FAILED,
    ROLLED_BACK
}
