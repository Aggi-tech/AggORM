package com.aggitech.orm.spring.migrations

import com.aggitech.orm.migrations.core.Migration
import com.aggitech.orm.spring.autoconfigure.MigrationRegistry
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.Configuration
import org.springframework.core.type.filter.AssignableTypeFilter
import kotlin.reflect.KClass

/**
 * Configuração base para migrations.
 *
 * Para usar migrations automáticas, crie uma classe de configuração que estende esta
 * e anota com @EnableAggoMigrations, especificando o pacote base onde suas migrations estão:
 *
 * ```kotlin
 * @Configuration
 * @EnableAggoMigrations(basePackages = ["com.example.migrations"])
 * class DatabaseMigrationConfig : MigrationConfiguration()
 * ```
 *
 * Alternativamente, registre migrations manualmente:
 *
 * ```kotlin
 * @Configuration
 * class DatabaseMigrationConfig(migrationRegistry: MigrationRegistry) {
 *     init {
 *         migrationRegistry.register(V001_CreateUsersTable::class)
 *         migrationRegistry.register(V002_CreatePostsTable::class)
 *     }
 * }
 * ```
 */
@Configuration
abstract class MigrationConfiguration

/**
 * Annotation para habilitar scan automático de migrations
 *
 * Usage:
 * ```kotlin
 * @Configuration
 * @EnableAggoMigrations(basePackages = ["com.example.migrations"])
 * class MigrationConfig : MigrationConfiguration()
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class EnableAggoMigrations(
    /**
     * Pacotes base onde fazer scan de migrations
     */
    val basePackages: Array<String> = []
)

/**
 * Scanner de migrations no classpath
 */
class MigrationClasspathScanner {

    /**
     * Faz scan de todas as classes que estendem Migration nos pacotes especificados
     */
    fun scanMigrations(basePackages: List<String>): List<KClass<out Migration>> {
        val migrations = mutableListOf<KClass<out Migration>>()

        val scanner = ClassPathScanningCandidateComponentProvider(false)
        scanner.addIncludeFilter(AssignableTypeFilter(Migration::class.java))

        for (basePackage in basePackages) {
            val candidates: Set<BeanDefinition> = scanner.findCandidateComponents(basePackage)

            for (candidate in candidates) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val clazz = Class.forName(candidate.beanClassName).kotlin as KClass<out Migration>
                    migrations.add(clazz)
                } catch (e: Exception) {
                    // Ignora classes que não podem ser carregadas
                }
            }
        }

        return migrations.sortedBy { it.simpleName }
    }
}
