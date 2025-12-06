package com.aggitech.orm.spring.autoconfigure

import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.enums.SupportedDatabases
import com.aggitech.orm.spring.transaction.AggoTransactionManager
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

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
     * Porta do servidor de banco de dados
     */
    var port: Int = 5432,

    /**
     * Nome de usuário para autenticação no banco de dados
     */
    var username: String = "",

    /**
     * Senha para autenticação no banco de dados
     */
    var password: String = "",

    /**
     * Tipo do banco de dados (POSTGRESQL, MYSQL)
     */
    var databaseType: String = "POSTGRESQL",

    /**
     * Dialeto SQL a ser usado (POSTGRESQL, POSTGRES, MYSQL)
     */
    var dialect: String = "POSTGRESQL",

    /**
     * Habilita ou desabilita a auto-configuração do AggORM
     */
    var enabled: Boolean = true
) {
    fun toDbConfig(): DbConfig {
        val dbType = try {
            SupportedDatabases.valueOf(databaseType.uppercase())
        } catch (e: Exception) {
            SupportedDatabases.POSTGRESQL
        }

        return DbConfig(
            database = database,
            host = host,
            port = port,
            user = username,
            password = password,
            type = dbType
        )
    }

    fun getSqlDialect(): SqlDialect {
        return when (dialect.uppercase()) {
            "POSTGRESQL", "POSTGRES" -> com.aggitech.orm.enums.PostgresDialect
            "MYSQL" -> com.aggitech.orm.enums.MySqlDialect
            else -> com.aggitech.orm.enums.PostgresDialect
        }
    }
}

/**
 * AutoConfiguration para AggORM no Spring Boot
 */
@AutoConfiguration
@ConditionalOnClass(DbConfig::class)
@EnableConfigurationProperties(AggoOrmProperties::class)
@EnableTransactionManagement
class AggoOrmAutoConfiguration(
    private val properties: AggoOrmProperties
) {

    /**
     * Cria o DbConfig bean baseado nas properties
     */
    @Bean
    @ConditionalOnMissingBean
    fun aggoDbConfig(): DbConfig {
        return properties.toDbConfig()
    }

    /**
     * Cria o SqlDialect bean baseado nas properties
     */
    @Bean
    @ConditionalOnMissingBean
    fun aggoSqlDialect(): SqlDialect {
        return properties.getSqlDialect()
    }

    /**
     * Cria o TransactionManager para suporte a @Transactional
     */
    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager::class)
    fun aggoTransactionManager(dbConfig: DbConfig): AggoTransactionManager {
        return AggoTransactionManager(dbConfig)
    }
}

/**
 * Configuração adicional para integração com DataSource do Spring
 * Só cria DbConfig a partir do DataSource se as properties do AggORM não estiverem configuradas
 */
@AutoConfiguration(after = [AggoOrmAutoConfiguration::class])
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

        return DbConfig(
            database = database,
            host = host,
            port = port,
            user = metaData.userName ?: "",
            password = "", // Não podemos extrair a senha do DataSource
            type = dbType
        )
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
