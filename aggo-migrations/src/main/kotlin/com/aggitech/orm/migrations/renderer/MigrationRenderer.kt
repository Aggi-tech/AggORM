package com.aggitech.orm.migrations.renderer

import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.migrations.core.MigrationOperation

/**
 * Interface for rendering migration operations to database-specific SQL.
 * Different implementations handle different SQL dialects.
 */
interface MigrationRenderer {

    /**
     * Render a migration operation to one or more SQL statements.
     *
     * @param operation The migration operation to render
     * @return List of SQL statements (may be multiple for complex operations)
     */
    fun render(operation: MigrationOperation): List<String>

    /**
     * The SQL dialect this renderer supports.
     */
    val dialect: SqlDialect
}
