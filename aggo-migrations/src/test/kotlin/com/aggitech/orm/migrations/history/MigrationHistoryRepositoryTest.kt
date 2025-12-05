package com.aggitech.orm.migrations.history

import com.aggitech.orm.enums.PostgresDialect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
class MigrationHistoryRepositoryTest {

    @Container
    private val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
        withDatabaseName("test")
        withUsername("test")
        withPassword("test")
    }

    private lateinit var connection: Connection
    private lateinit var repository: JdbcMigrationHistoryRepository

    @BeforeEach
    fun setup() {
        connection = DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        )
        repository = JdbcMigrationHistoryRepository(
            connection = connection,
            dialect = PostgresDialect
        )
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `should create history table on first use`() {
        repository.ensureHistoryTableExists()

        val sql = """
            SELECT EXISTS (
                SELECT FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name = 'aggo_migration_history'
            )
        """.trimIndent()

        val exists = connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                rs.next() && rs.getBoolean(1)
            }
        }

        assertTrue(exists, "History table should be created")
    }

    @Test
    fun `should save and retrieve migration records`() {
        repository.ensureHistoryTableExists()

        val record = MigrationRecord(
            version = 1,
            timestamp = 20231201120000L,
            description = "Create users table",
            checksum = "abc123",
            executedAt = Instant.now(),
            executionTimeMs = 100L,
            status = MigrationStatus.SUCCESS
        )

        val saved = repository.save(record)

        assertNotNull(saved.id, "Saved record should have an ID")
        assertEquals(record.version, saved.version)
        assertEquals(record.description, saved.description)

        val retrieved = repository.findByVersion(1)

        assertNotNull(retrieved)
        assertEquals(saved.id, retrieved.id)
        assertEquals(saved.checksum, retrieved.checksum)
    }

    @Test
    fun `should detect already applied migrations`() {
        repository.ensureHistoryTableExists()

        assertFalse(repository.isApplied(1), "Migration 1 should not be applied initially")

        val record = MigrationRecord(
            version = 1,
            timestamp = 20231201120000L,
            description = "Test migration",
            checksum = "abc123",
            executedAt = Instant.now(),
            executionTimeMs = 50L,
            status = MigrationStatus.SUCCESS
        )

        repository.save(record)

        assertTrue(repository.isApplied(1), "Migration 1 should be applied after saving")
    }

    @Test
    fun `should validate checksums correctly`() {
        repository.ensureHistoryTableExists()

        val record = MigrationRecord(
            version = 1,
            timestamp = 20231201120000L,
            description = "Test migration",
            checksum = "original-checksum",
            executedAt = Instant.now(),
            executionTimeMs = 50L,
            status = MigrationStatus.SUCCESS
        )

        repository.save(record)

        assertTrue(
            repository.validateChecksum(1, "original-checksum"),
            "Checksum should match"
        )

        assertFalse(
            repository.validateChecksum(1, "modified-checksum"),
            "Checksum should not match when different"
        )
    }

    @Test
    fun `should track failed migrations`() {
        repository.ensureHistoryTableExists()

        val record = MigrationRecord(
            version = 2,
            timestamp = 20231201130000L,
            description = "Failed migration",
            checksum = "def456",
            executedAt = Instant.now(),
            executionTimeMs = 25L,
            status = MigrationStatus.FAILED,
            errorMessage = "SQL syntax error"
        )

        repository.save(record)

        val retrieved = repository.findByVersion(2)

        assertNotNull(retrieved)
        assertEquals(MigrationStatus.FAILED, retrieved.status)
        assertEquals("SQL syntax error", retrieved.errorMessage)
    }

    @Test
    fun `should get latest migration`() {
        repository.ensureHistoryTableExists()

        assertNull(repository.getLatest(), "Should be null when no migrations applied")

        val record1 = MigrationRecord(
            version = 1,
            timestamp = 20231201120000L,
            description = "First migration",
            checksum = "abc123",
            executedAt = Instant.now(),
            executionTimeMs = 50L,
            status = MigrationStatus.SUCCESS
        )

        val record2 = MigrationRecord(
            version = 2,
            timestamp = 20231201130000L,
            description = "Second migration",
            checksum = "def456",
            executedAt = Instant.now(),
            executionTimeMs = 75L,
            status = MigrationStatus.SUCCESS
        )

        repository.save(record1)
        repository.save(record2)

        val latest = repository.getLatest()

        assertNotNull(latest)
        assertEquals(2, latest.version, "Latest should be version 2")
        assertEquals("Second migration", latest.description)
    }

    @Test
    fun `should find all migrations ordered by version`() {
        repository.ensureHistoryTableExists()

        val records = listOf(
            MigrationRecord(
                version = 3,
                timestamp = 20231201150000L,
                description = "Third",
                checksum = "ghi789",
                executedAt = Instant.now(),
                executionTimeMs = 100L,
                status = MigrationStatus.SUCCESS
            ),
            MigrationRecord(
                version = 1,
                timestamp = 20231201120000L,
                description = "First",
                checksum = "abc123",
                executedAt = Instant.now(),
                executionTimeMs = 50L,
                status = MigrationStatus.SUCCESS
            ),
            MigrationRecord(
                version = 2,
                timestamp = 20231201130000L,
                description = "Second",
                checksum = "def456",
                executedAt = Instant.now(),
                executionTimeMs = 75L,
                status = MigrationStatus.SUCCESS
            )
        )

        records.forEach { repository.save(it) }

        val all = repository.findAll()

        assertEquals(3, all.size)
        assertEquals(1, all[0].version, "First should be version 1")
        assertEquals(2, all[1].version, "Second should be version 2")
        assertEquals(3, all[2].version, "Third should be version 3")
    }

    @Test
    fun `should return null for non-existent version`() {
        repository.ensureHistoryTableExists()

        val record = repository.findByVersion(999)

        assertNull(record, "Should return null for non-existent version")
    }
}
