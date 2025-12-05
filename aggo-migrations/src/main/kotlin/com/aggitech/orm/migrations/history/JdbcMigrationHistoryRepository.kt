package com.aggitech.orm.migrations.history

import com.aggitech.orm.enums.SqlDialect
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.time.Instant

/**
 * JDBC implementation of the migration history repository.
 * Manages migration history records using JDBC connections.
 *
 * @param connection JDBC connection to use for database operations
 * @param dialect SQL dialect for the target database
 * @param tableName Name of the migration history table (default: "aggo_migration_history")
 * @param schema Database schema (default: "public")
 */
class JdbcMigrationHistoryRepository(
    private val connection: Connection,
    private val dialect: SqlDialect,
    private val tableName: String = "aggo_migration_history",
    private val schema: String = "public"
) : MigrationHistoryRepository {

    private val fullTableName: String = if (schema.isNotEmpty()) {
        "${quote(schema)}.${quote(tableName)}"
    } else {
        quote(tableName)
    }

    override fun ensureHistoryTableExists() {
        val sql = """
            CREATE TABLE IF NOT EXISTS $fullTableName (
                id BIGSERIAL PRIMARY KEY,
                version INT NOT NULL UNIQUE,
                timestamp BIGINT NOT NULL,
                description VARCHAR(255) NOT NULL,
                checksum VARCHAR(64) NOT NULL,
                executed_at TIMESTAMP NOT NULL,
                execution_time_ms BIGINT NOT NULL,
                status VARCHAR(20) NOT NULL,
                error_message TEXT,
                applied_by VARCHAR(100) NOT NULL,
                class_name VARCHAR(500),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()

        connection.createStatement().use { stmt ->
            stmt.execute(sql)
        }
    }

    override fun findAll(): List<MigrationRecord> {
        val sql = "SELECT * FROM $fullTableName ORDER BY version ASC"

        return connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                buildList {
                    while (rs.next()) {
                        add(mapResultSet(rs))
                    }
                }
            }
        }
    }

    override fun findByVersion(version: Int): MigrationRecord? {
        val sql = "SELECT * FROM $fullTableName WHERE version = ?"

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, version)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapResultSet(rs) else null
            }
        }
    }

    override fun isApplied(version: Int): Boolean {
        val sql = "SELECT COUNT(*) FROM $fullTableName WHERE version = ? AND status = ?"

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, version)
            stmt.setString(2, MigrationStatus.SUCCESS.name)
            stmt.executeQuery().use { rs ->
                rs.next() && rs.getInt(1) > 0
            }
        }
    }

    override fun save(record: MigrationRecord): MigrationRecord {
        val sql = """
            INSERT INTO $fullTableName
            (version, timestamp, description, checksum, executed_at, execution_time_ms,
             status, error_message, applied_by, class_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            stmt.setInt(1, record.version)
            stmt.setLong(2, record.timestamp)
            stmt.setString(3, record.description)
            stmt.setString(4, record.checksum)
            stmt.setTimestamp(5, java.sql.Timestamp.from(record.executedAt))
            stmt.setLong(6, record.executionTimeMs)
            stmt.setString(7, record.status.name)
            stmt.setString(8, record.errorMessage)
            stmt.setString(9, record.appliedBy)
            stmt.setString(10, record.className)

            stmt.executeUpdate()

            stmt.generatedKeys.use { rs ->
                if (rs.next()) {
                    record.copy(id = rs.getLong(1))
                } else {
                    record
                }
            }
        }
    }

    override fun getLatest(): MigrationRecord? {
        val sql = """
            SELECT * FROM $fullTableName
            WHERE status = ?
            ORDER BY version DESC
            LIMIT 1
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, MigrationStatus.SUCCESS.name)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapResultSet(rs) else null
            }
        }
    }

    override fun validateChecksum(version: Int, expectedChecksum: String): Boolean {
        val record = findByVersion(version) ?: return true
        return record.checksum == expectedChecksum
    }

    private fun mapResultSet(rs: ResultSet): MigrationRecord {
        return MigrationRecord(
            id = rs.getLong("id"),
            version = rs.getInt("version"),
            timestamp = rs.getLong("timestamp"),
            description = rs.getString("description"),
            checksum = rs.getString("checksum"),
            executedAt = rs.getTimestamp("executed_at").toInstant(),
            executionTimeMs = rs.getLong("execution_time_ms"),
            status = MigrationStatus.valueOf(rs.getString("status")),
            errorMessage = rs.getString("error_message"),
            appliedBy = rs.getString("applied_by"),
            className = rs.getString("class_name")
        )
    }

    private fun quote(identifier: String): String {
        return "${dialect.quoteChar}$identifier${dialect.quoteChar}"
    }
}
