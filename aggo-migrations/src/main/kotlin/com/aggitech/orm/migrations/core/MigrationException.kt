package com.aggitech.orm.migrations.core

/**
 * Exception thrown when migration operations fail.
 */
class MigrationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
