package com.aggitech.orm.spring.autoconfigure

import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.migrations.core.Migration
import com.aggitech.orm.migrations.executor.JdbcMigrationExecutor
import com.aggitech.orm.migrations.executor.MigrationExecutor
import com.aggitech.orm.migrations.executor.MigrationScanner
import com.aggitech.orm.migrations.history.JdbcMigrationHistoryRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.stereotype.Component
import java.sql.DriverManager
import kotlin.reflect.KClass

/**
 * Properties de configuração para migrations do AggORM
 */
@ConfigurationProperties(prefix = "aggo.orm.migrations")
data class AggoMigrationsProperties(
    /**
     * Habilita a execução automática de migrations ao iniciar a aplicação
     */
    var enabled: Boolean = true,

    /**
     * Pacote onde as migrations estão localizadas (ex: "com.example.migrations")
     * Se não especificado, não faz scan automático
     */
    var basePackage: String = "",

    /**
     * Se true, exibe detalhes das migrations executadas
     */
    var showDetails: Boolean = true,

    /**
     * Se true, para a aplicação se alguma migration falhar
     */
    var failOnError: Boolean = true,

    /**
     * Se true, valida checksums de migrations já aplicadas
     */
    var validateChecksums: Boolean = true
)

/**
 * AutoConfiguration para Migrations do AggORM no Spring Boot
 *
 * Executa migrations automaticamente ao iniciar a aplicação Spring Boot
 * sem necessidade de configuração manual.
 *
 * Configuração via application.yml:
 * ```yaml
 * aggo:
 *   orm:
 *     migrations:
 *       enabled: true
 *       base-package: "com.example.migrations"
 *       show-details: true
 *       fail-on-error: true
 * ```
 */
@AutoConfiguration(after = [AggoOrmAutoConfiguration::class])
@ConditionalOnClass(Migration::class)
@ConditionalOnProperty(prefix = "aggo.orm.migrations", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AggoMigrationsProperties::class)
class AggoMigrationsAutoConfiguration {

    private val logger = LoggerFactory.getLogger(AggoMigrationsAutoConfiguration::class.java)

    /**
     * Cria o bean do MigrationExecutor
     */
    @Bean
    fun migrationExecutor(dbConfig: DbConfig, dialect: SqlDialect): MigrationExecutor {
        val connection = DriverManager.getConnection(
            dbConfig.getJdbcUrl(),
            dbConfig.user,
            dbConfig.password
        )

        val historyRepository = JdbcMigrationHistoryRepository(connection, dialect)
        return JdbcMigrationExecutor(connection, dialect, historyRepository)
    }

    /**
     * Cria o bean do MigrationScanner
     */
    @Bean
    fun migrationScanner(): MigrationScanner {
        return MigrationScanner()
    }

    /**
     * Executor de migrations que roda na inicialização do Spring
     */
    @Bean
    fun migrationRunner(
        migrationExecutor: MigrationExecutor,
        migrationScanner: MigrationScanner,
        properties: AggoMigrationsProperties,
        migrationRegistry: MigrationRegistry
    ): MigrationRunner {
        return MigrationRunner(migrationExecutor, migrationScanner, properties, migrationRegistry)
    }

    /**
     * Registry para coletar todas as migrations declaradas como beans
     */
    @Bean
    fun migrationRegistry(): MigrationRegistry {
        return MigrationRegistry()
    }
}

/**
 * Registry que coleta automaticamente todas as migrations disponíveis
 */
@Component
class MigrationRegistry {
    private val migrations = mutableListOf<KClass<out Migration>>()

    fun register(migrationClass: KClass<out Migration>) {
        migrations.add(migrationClass)
    }

    fun getAllMigrations(): List<KClass<out Migration>> = migrations.toList()
}

/**
 * Runner que executa as migrations automaticamente na inicialização
 */
@Component
class MigrationRunner(
    private val migrationExecutor: MigrationExecutor,
    private val migrationScanner: MigrationScanner,
    private val properties: AggoMigrationsProperties,
    private val migrationRegistry: MigrationRegistry
) {

    private val logger = LoggerFactory.getLogger(MigrationRunner::class.java)

    @PostConstruct
    fun runMigrations() {
        if (!properties.enabled) {
            logger.info("AggORM Migrations: Disabled via configuration")
            return
        }

        try {
            logger.info("AggORM Migrations: Starting migration process...")

            // Carrega migrations do registry
            val migrationClasses = migrationRegistry.getAllMigrations()

            if (migrationClasses.isEmpty()) {
                logger.info("AggORM Migrations: No migrations found. " +
                    "Please register your migrations by implementing Migration class.")
                return
            }

            val migrations = migrationScanner.loadMigrations(migrationClasses)
            logger.info("AggORM Migrations: Found ${migrations.size} migration(s)")

            // Valida estado antes de executar
            if (properties.validateChecksums) {
                val validation = migrationExecutor.validate(migrations)
                if (!validation.valid) {
                    val errorMsg = "Migration validation failed:\n" +
                        validation.issues.joinToString("\n") { "  - $it" }
                    logger.error("AggORM Migrations: $errorMsg")

                    if (properties.failOnError) {
                        throw RuntimeException(errorMsg)
                    }
                }
            }

            // Executa migrations
            val result = migrationExecutor.migrate(migrations)

            // Log dos resultados
            if (properties.showDetails) {
                result.executed.forEach {
                    logger.info("AggORM Migrations: [OK] V${it.version} - ${it.description} (${it.executionTimeMs}ms)")
                }

                result.skipped.forEach {
                    logger.debug("AggORM Migrations: [SKIP] V${it.version} - ${it.description} (${it.reason})")
                }
            }

            if (result.failed.isNotEmpty()) {
                val failed = result.failed.first()
                logger.error("AggORM Migrations: [FAIL] V${failed.version} - ${failed.description}")
                logger.error("AggORM Migrations: Error: ${failed.error.message}")

                if (properties.failOnError) {
                    throw failed.error
                }
            }

            logger.info("AggORM Migrations: Completed successfully. " +
                "Executed: ${result.totalExecuted}, " +
                "Skipped: ${result.totalSkipped}, " +
                "Failed: ${result.totalFailed}")

        } catch (e: Exception) {
            logger.error("AggORM Migrations: Failed to run migrations", e)

            if (properties.failOnError) {
                throw RuntimeException("Migration failed", e)
            }
        }
    }
}
