package com.aggitech.orm.migrations.executor

import com.aggitech.orm.enums.PostgresDialect
import com.aggitech.orm.migrations.core.CascadeType
import com.aggitech.orm.migrations.core.Migration
import com.aggitech.orm.migrations.dsl.ColumnType
import com.aggitech.orm.migrations.history.JdbcMigrationHistoryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Testcontainers
class JdbcMigrationExecutorTest {

    @Container
    private val postgres = PostgreSQLContainer<Nothing>("postgres:15-alpine").apply {
        withDatabaseName("test")
        withUsername("test")
        withPassword("test")
    }

    private lateinit var connection: Connection
    private lateinit var historyRepository: JdbcMigrationHistoryRepository
    private lateinit var executor: JdbcMigrationExecutor

    @BeforeEach
    fun setup() {
        connection = DriverManager.getConnection(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password
        )
        historyRepository = JdbcMigrationHistoryRepository(
            connection = connection,
            dialect = PostgresDialect
        )
        executor = JdbcMigrationExecutor(
            connection = connection,
            dialect = PostgresDialect,
            historyRepository = historyRepository
        )
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `should execute pending migrations in order`() {
        val migrations = listOf(V001_20231201_CreateUsers(), V002_20231202_CreatePosts())

        val result = executor.migrate(migrations)

        assertTrue(result.success, "Migration should succeed")
        assertEquals(2, result.totalExecuted, "Should execute 2 migrations")
        assertEquals(0, result.totalFailed, "Should have no failures")
        assertEquals(0, result.totalSkipped, "Should have no skipped")

        // Verify tables were created
        val tablesExist = connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                AND table_name IN ('users', 'posts')
                ORDER BY table_name
            """.trimIndent())

            val tables = mutableListOf<String>()
            while (rs.next()) {
                tables.add(rs.getString(1))
            }
            tables
        }

        assertEquals(listOf("posts", "users"), tablesExist.sorted())
    }

    @Test
    fun `should skip already applied migrations`() {
        val migrations = listOf(V001_20231201_CreateUsers())

        // Apply first time
        val result1 = executor.migrate(migrations)
        assertTrue(result1.success)
        assertEquals(1, result1.totalExecuted)

        // Apply second time
        val result2 = executor.migrate(migrations)
        assertTrue(result2.success)
        assertEquals(0, result2.totalExecuted)
        assertEquals(1, result2.totalSkipped, "Should skip already applied migration")
        assertEquals("Already applied", result2.skipped[0].reason)
    }

    @Test
    fun `should rollback on migration failure`() {
        val migrations = listOf(V003_20231203_InvalidSql())

        val result = executor.migrate(migrations)

        assertFalse(result.success, "Migration should fail")
        assertEquals(0, result.totalExecuted)
        assertEquals(1, result.totalFailed)

        // Verify table was NOT created (rolled back)
        val tableExists = connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("""
                SELECT EXISTS (
                    SELECT FROM information_schema.tables
                    WHERE table_schema = 'public'
                    AND table_name = 'broken_table'
                )
            """.trimIndent())
            rs.next() && rs.getBoolean(1)
        }

        assertFalse(tableExists, "Table should not exist after rollback")
    }

    @Test
    fun `should detect checksum mismatch`() {
        val migration1 = V001_20231201_CreateUsers()

        // Apply migration
        executor.migrate(listOf(migration1))

        // Use a modified version of the same migration class
        val migration2 = V001_20231201_CreateUsersModified()

        try {
            executor.migrate(listOf(migration2))
            throw AssertionError("Should have thrown checksum mismatch exception")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Checksum mismatch"), "Should detect checksum mismatch")
        }
    }

    @Test
    fun `should execute complex migration with multiple operations`() {
        val migration = V004_20231204_ComplexMigration()

        val result = executor.migrate(listOf(migration))

        assertTrue(result.success)
        assertEquals(1, result.totalExecuted)

        // Verify all operations were executed
        val columnsExist = connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                AND table_name = 'products'
                ORDER BY column_name
            """.trimIndent())

            val columns = mutableListOf<String>()
            while (rs.next()) {
                columns.add(rs.getString(1))
            }
            columns
        }

        assertTrue(columnsExist.contains("id"))
        assertTrue(columnsExist.contains("name"))
        assertTrue(columnsExist.contains("price"))
    }

    @Test
    fun `should provide migration status`() {
        val migrations = listOf(
            V001_20231201_CreateUsers(),
            V002_20231202_CreatePosts(),
            V004_20231204_ComplexMigration()
        )

        // Apply only first migration
        executor.migrate(listOf(migrations[0]))

        val status = executor.status(migrations)

        assertEquals(1, status.appliedCount)
        assertEquals(2, status.pendingCount)
        assertEquals(1, status.appliedMigrations[0].version)
        assertEquals("CreateUsers", status.appliedMigrations[0].description)
        assertEquals(listOf(2, 4), status.pendingMigrations.map { it.version })
    }

    @Test
    fun `should validate applied migrations`() {
        val migrations = listOf(V001_20231201_CreateUsers())

        executor.migrate(migrations)

        val validation = executor.validate(migrations)

        assertTrue(validation.valid, "Validation should pass")
        assertTrue(validation.issues.isEmpty(), "Should have no issues")
    }
}

// Test migrations

class V001_20231201_CreateUsers : Migration() {
    override fun up() {
        createTable("users") {
            column { bigInteger("id").primaryKey().autoIncrement() }
            column { varchar("name", 100).notNull() }
            column { varchar("email", 255).notNull().unique() }
            column { timestamp("created_at").notNull().default("CURRENT_TIMESTAMP") }
        }
    }

    override fun down() {
        dropTable("users")
    }
}

class V002_20231202_CreatePosts : Migration() {
    override fun up() {
        createTable("posts") {
            column { bigInteger("id").primaryKey().autoIncrement() }
            column { varchar("title", 200).notNull() }
            column { text("content").notNull() }
            column { bigInteger("user_id").notNull() }
        }
    }

    override fun down() {
        dropTable("posts")
    }
}

class V003_20231203_InvalidSql : Migration() {
    override fun up() {
        executeSql("CREATE TABLE broken_table (id INVALID_TYPE)")
    }

    override fun down() {
        dropTable("broken_table")
    }
}

class V004_20231204_ComplexMigration : Migration() {
    override fun up() {
        createTable("products") {
            column { bigInteger("id").primaryKey().autoIncrement() }
            column { varchar("name", 200).notNull() }
        }

        addColumn("products") {
            varchar("description", 500)
        }

        addColumn("products") {
            bigInteger("price").notNull().default("0")
        }
    }

    override fun down() {
        dropTable("products")
    }
}

/**
 * Modified version of V001 migration for checksum mismatch testing.
 * Has same version (V001) and timestamp but different operations.
 */
class V001_20231201_CreateUsersModified : Migration() {
    override fun up() {
        createTable("users") {
            column { bigInteger("id").primaryKey().autoIncrement() }
            column { varchar("name", 100).notNull() }
            column { varchar("email", 255).notNull().unique() }
            // MODIFIED: Added new column
            column { varchar("phone", 20) }
        }
    }

    override fun down() {
        dropTable("users")
    }
}
