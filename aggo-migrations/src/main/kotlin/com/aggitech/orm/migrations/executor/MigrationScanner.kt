package com.aggitech.orm.migrations.executor

import com.aggitech.orm.migrations.core.Migration
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

/**
 * Scans and loads migration classes.
 * Currently supports loading from an explicit list of migration classes.
 *
 * Future enhancement: Add classpath scanning using reflection libraries.
 *
 * @param basePackage Base package for migrations (currently not used, reserved for future scanning)
 */
class MigrationScanner(
    private val basePackage: String = "db.migrations"
) {

    /**
     * Load migrations from an explicit list of migration classes.
     *
     * Example usage:
     * ```
     * val scanner = MigrationScanner()
     * val migrations = scanner.loadMigrations(listOf(
     *     V001_20231201_CreateUsers::class,
     *     V002_20231202_AddEmailIndex::class
     * ))
     * ```
     *
     * @param migrationClasses List of migration classes to instantiate
     * @return List of instantiated migration objects
     */
    fun loadMigrations(migrationClasses: List<KClass<out Migration>>): List<Migration> {
        return migrationClasses.map { kClass ->
            try {
                kClass.createInstance()
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Failed to instantiate migration class: ${kClass.qualifiedName}. " +
                            "Ensure the class has a no-arg constructor.",
                    e
                )
            }
        }
    }

    /**
     * Scan for migration classes in the classpath (NOT YET IMPLEMENTED).
     *
     * This method is a placeholder for future implementation using
     * reflection libraries like org.reflections:reflections.
     *
     * @throws UnsupportedOperationException This feature is not yet implemented
     */
    fun scanMigrations(): List<Migration> {
        throw UnsupportedOperationException(
            "Automatic classpath scanning is not yet implemented. " +
                    "Use loadMigrations(List<KClass<out Migration>>) with an explicit list of migration classes. " +
                    "Future enhancement: Add classpath scanning library (e.g., org.reflections:reflections)."
        )
    }
}
