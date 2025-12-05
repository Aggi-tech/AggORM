package com.aggitech.orm.migrations.renderer

import com.aggitech.orm.enums.MySqlDialect
import com.aggitech.orm.enums.PostgresDialect
import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.migrations.core.MigrationException

/**
 * Factory for creating dialect-specific migration renderers.
 */
object MigrationRendererFactory {

    /**
     * Create a renderer for the given SQL dialect.
     *
     * @param dialect The SQL dialect to create a renderer for
     * @return A renderer instance for the dialect
     * @throws MigrationException if the dialect is not supported
     */
    fun createRenderer(dialect: SqlDialect): MigrationRenderer {
        return when (dialect) {
            is PostgresDialect -> PostgresMigrationRenderer()
            is MySqlDialect -> throw MigrationException("MySQL support not yet implemented")
            else -> throw MigrationException("Unsupported dialect: $dialect")
        }
    }
}
