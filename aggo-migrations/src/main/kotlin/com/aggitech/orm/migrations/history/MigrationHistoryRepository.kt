package com.aggitech.orm.migrations.history

/**
 * Repository for managing migration history records.
 * Provides CRUD operations for tracking applied migrations.
 *
 * Supports both JDBC and R2DBC implementations through different concrete classes.
 */
interface MigrationHistoryRepository {

    /**
     * Ensure the migration history table exists in the database.
     * Creates the table if it doesn't exist.
     */
    fun ensureHistoryTableExists()

    /**
     * Find all migration records ordered by version ascending.
     *
     * @return List of all migration records
     */
    fun findAll(): List<MigrationRecord>

    /**
     * Find a specific migration by its version number.
     *
     * @param version Migration version to find
     * @return Migration record if found, null otherwise
     */
    fun findByVersion(version: Int): MigrationRecord?

    /**
     * Check if a migration with the given version has been successfully applied.
     *
     * @param version Migration version to check
     * @return true if migration was applied successfully, false otherwise
     */
    fun isApplied(version: Int): Boolean

    /**
     * Save or update a migration record.
     *
     * @param record Migration record to save
     * @return Saved record with generated ID if applicable
     */
    fun save(record: MigrationRecord): MigrationRecord

    /**
     * Get the latest successfully applied migration.
     *
     * @return Latest migration record or null if none applied
     */
    fun getLatest(): MigrationRecord?

    /**
     * Validate that the checksum of an applied migration matches the expected value.
     * Used to detect if a migration file was modified after being applied.
     *
     * @param version Migration version to validate
     * @param expectedChecksum Expected checksum value
     * @return true if checksum matches or migration not found, false if mismatch detected
     */
    fun validateChecksum(version: Int, expectedChecksum: String): Boolean
}
