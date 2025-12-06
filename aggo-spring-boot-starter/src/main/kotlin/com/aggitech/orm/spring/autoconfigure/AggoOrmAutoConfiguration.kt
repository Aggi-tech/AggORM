package com.aggitech.orm.spring.autoconfigure

import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.enums.SupportedDatabases
import com.aggitech.orm.jdbc.JdbcConnectionManager
import com.aggitech.orm.spring.transaction.AggoTransactionManager
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

/**
 * Enum para dialeto SQL usado nas properties do Spring Boot
 * @deprecated Use SqlDialect diretamente do aggo-core
 */
@Deprecated("Use SqlDialect do aggo-core diretamente", ReplaceWith("SupportedDatabases"))
enum class AggoDialect(val sqlDialect: SqlDialect, val supportedDatabase: SupportedDatabases) {
    POSTGRESQL(com.aggitech.orm.enums.PostgresDialect, SupportedDatabases.POSTGRESQL),
    MYSQL(com.aggitech.orm.enums.MySqlDialect, SupportedDatabases.MYSQL)
}

/**
 * Properties de configuração do AggORM para Spring Boot
 */
@ConfigurationProperties(prefix = "aggo.orm")
data class AggoOrmProperties(
    /**
     * Nome do banco de dados para conexão
     */
    var database: String = "",

    /**
     * Hostname do servidor de banco de dados
     */
    var host: String = "localhost",

    /**
     * Porta do servidor de banco de dados (default baseado no dialect)
     */
    var port: Int? = null,

    /**
     * Nome de usuário para autenticação no banco de dados
     */
    var username: String = "",

    /**
     * Senha para autenticação no banco de dados
     */
    var password: String = "",

    /**
     * Dialeto SQL - define automaticamente o tipo de banco e porta padrão
     */
    var dialect: AggoDialect = AggoDialect.POSTGRESQL,

    /**
     * Habilita ou desabilita a auto-configuração do AggORM
     */
    var enabled: Boolean = true
) {
    fun toDbConfig(): DbConfig {
        return DbConfig(
            database = database,
            host = host,
            port = port ?: dialect.supportedDatabase.defaultPort,
            user = username,
            password = password,
            type = dialect.supportedDatabase
        )
    }

    fun getSqlDialect(): SqlDialect {
        return dialect.sqlDialect
    }
}

/**
 * AutoConfiguration para AggORM no Spring Boot
 *
 * Esta configuração executa APÓS DataSourceAutoConfiguration para garantir
 * que o DataSource já esteja configurado quando necessário.
 *
 * JPA é completamente opcional - se presente, AggORM não interfere.
 * Se ausente, AggORM fornece seu próprio TransactionManager.
 */
@AutoConfiguration(after = [DataSourceAutoConfiguration::class])
@ConditionalOnClass(DbConfig::class)
@ConditionalOnProperty(prefix = "aggo.orm", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AggoOrmProperties::class)
@EnableTransactionManagement
class AggoOrmAutoConfiguration(
    private val properties: AggoOrmProperties
) {

    /**
     * Cria o DbConfig bean baseado nas properties
     * Também registra no JdbcConnectionManager para uso direto
     */
    @Bean
    @ConditionalOnMissingBean
    fun aggoDbConfig(): DbConfig {
        val config = properties.toDbConfig()
        // Registra automaticamente no JdbcConnectionManager
        if (config.database.isNotBlank()) {
            JdbcConnectionManager.register("default", config)
        }
        return config
    }

    /**
     * Cria o SqlDialect bean baseado nas properties
     */
    @Bean
    @ConditionalOnMissingBean
    fun aggoSqlDialect(): SqlDialect {
        return properties.getSqlDialect()
    }
}

/**
 * Configuração do TransactionManager do AggORM
 * Só é ativada quando JPA/Hibernate NÃO está presente no classpath
 */
@Configuration
@ConditionalOnMissingClass("jakarta.persistence.EntityManagerFactory")
@ConditionalOnClass(DbConfig::class)
class AggoTransactionManagerConfiguration {

    /**
     * Cria o TransactionManager para suporte a @Transactional
     * Apenas quando não há JPA presente
     */
    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager::class)
    fun aggoTransactionManager(dbConfig: DbConfig): AggoTransactionManager {
        return AggoTransactionManager(dbConfig)
    }
}

/**
 * Configuração adicional para integração com DataSource do Spring
 *
 * Só cria DbConfig a partir do DataSource se:
 * 1. As properties do AggORM não estiverem configuradas
 * 2. Já existir um DataSource configurado
 *
 * Executa APÓS AggoOrmAutoConfiguration e DataSourceAutoConfiguration
 */
@AutoConfiguration(after = [AggoOrmAutoConfiguration::class, DataSourceAutoConfiguration::class])
@ConditionalOnClass(DataSource::class)
class AggoDataSourceConfiguration {

    /**
     * Se o usuário já tem um DataSource configurado no Spring,
     * podemos criar um DbConfig a partir dele
     */
    @Bean
    @ConditionalOnMissingBean(DbConfig::class)
    fun dbConfigFromDataSource(dataSource: DataSource): DbConfig {
        // Extrai informações do DataSource
        val connection = dataSource.connection
        val metaData = connection.metaData

        connection.close()

        // Parse da URL JDBC
        val url = metaData.url
        val (host, port, database) = parseJdbcUrl(url)

        val dbType = when {
            url.contains("postgresql") -> SupportedDatabases.POSTGRESQL
            url.contains("mysql") -> SupportedDatabases.MYSQL
            else -> SupportedDatabases.POSTGRESQL
        }

        val config = DbConfig(
            database = database,
            host = host,
            port = port,
            user = metaData.userName ?: "",
            password = "", // Não podemos extrair a senha do DataSource
            type = dbType
        )

        // Registra no JdbcConnectionManager para uso direto
        if (config.database.isNotBlank()) {
            JdbcConnectionManager.register("default", config)
        }

        return config
    }

    private fun parseJdbcUrl(url: String): Triple<String, Int, String> {
        // jdbc:postgresql://localhost:5432/mydb
        val pattern = """jdbc:\w+://([^:]+):(\d+)/(\w+)""".toRegex()
        val match = pattern.find(url)

        return if (match != null) {
            val (host, port, database) = match.destructured
            Triple(host, port.toInt(), database)
        } else {
            Triple("localhost", 5432, "")
        }
    }
}
