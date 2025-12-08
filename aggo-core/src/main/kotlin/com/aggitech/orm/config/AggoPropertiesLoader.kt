package com.aggitech.orm.config

import com.aggitech.orm.enums.SupportedDatabases
import java.io.InputStream
import java.util.Properties

/**
 * Configuração para geração de Mirrors.
 *
 * @param basePackage Pacote base para os arquivos gerados (ex: "com.myapp.generated.mirrors")
 * @param outputDir Diretório de saída (ex: "src/main/kotlin")
 * @param schemaName Nome do schema do banco de dados (default: "public")
 */
data class MirrorConfig(
    val basePackage: String = "generated.mirrors",
    val outputDir: String = "src/main/kotlin",
    val schemaName: String = "public"
)

/**
 * Container com todas as configurações do AggORM.
 *
 * @param dbConfig Configuração de conexão com o banco de dados
 * @param mirrorConfig Configuração para geração de Mirrors (opcional)
 */
data class AggoConfig(
    val dbConfig: DbConfig,
    val mirrorConfig: MirrorConfig = MirrorConfig()
)

/**
 * Loader de propriedades framework-agnostic para AggORM
 *
 * Suporta:
 * - application.properties
 * - application.yml / application.yaml
 *
 * Funciona com qualquer framework: Spring Boot, Quarkus, Micronaut, etc.
 *
 * Propriedades suportadas (prefixo: aggo.orm):
 * - aggo.orm.database (obrigatório)
 * - aggo.orm.username (obrigatório)
 * - aggo.orm.password (opcional, default: "")
 * - aggo.orm.host (opcional, default: localhost)
 * - aggo.orm.port (opcional, default: baseado no dialect)
 * - aggo.orm.dialect (opcional, default: POSTGRESQL) - valores: POSTGRESQL, MYSQL
 *
 * Propriedades de Mirror (dois prefixos suportados):
 *
 * Prefixo recomendado: aggo.orm.mirrors
 * - aggo.orm.mirrors.base-package (opcional, default: "generated.mirrors")
 * - aggo.orm.mirrors.output-dir (opcional, default: "src/main/kotlin")
 * - aggo.orm.mirrors.schema-name (opcional, default: "public")
 *
 * Prefixo alternativo (compatível com Spring Boot): aggo.orm.migrations.table-meta
 * - aggo.orm.migrations.table-meta.base-package
 * - aggo.orm.migrations.table-meta.output-dir
 * - aggo.orm.migrations.table-meta.schema-name
 *
 * Exemplo application.properties:
 * ```
 * aggo.orm.database=mydb
 * aggo.orm.host=localhost
 * aggo.orm.username=user
 * aggo.orm.password=pass
 * aggo.orm.dialect=POSTGRESQL
 * aggo.orm.mirrors.base-package=com.myapp.generated.mirrors
 * aggo.orm.mirrors.output-dir=src/main/kotlin
 * aggo.orm.mirrors.schema-name=public
 * ```
 *
 * Exemplo application.yml:
 * ```yaml
 * aggo:
 *   orm:
 *     database: mydb
 *     host: localhost
 *     username: user
 *     password: pass
 *     dialect: POSTGRESQL
 *     mirrors:
 *       base-package: com.myapp.generated.mirrors
 *       output-dir: src/main/kotlin
 *       schema-name: public
 * ```
 */
object AggoPropertiesLoader {

    private const val PREFIX = "aggo.orm"
    private const val MIRRORS_PREFIX = "aggo.orm.mirrors"
    private const val TABLE_META_PREFIX = "aggo.orm.migrations.table-meta"
    private const val TABLE_META_PREFIX_ALT = "aggo.orm.migrations.tableMeta"

    /**
     * Carrega DbConfig a partir do classpath
     * Procura por application.yml, application.yaml, application.properties nessa ordem
     *
     * @param classLoader ClassLoader para buscar recursos (default: context ou current)
     * @return DbConfig se encontrado e válido, null caso contrário
     */
    fun loadFromClasspath(classLoader: ClassLoader? = null): DbConfig? {
        return loadFullConfigFromClasspath(classLoader)?.dbConfig
    }

    /**
     * Carrega a configuração completa (DbConfig + MirrorConfig) a partir do classpath.
     *
     * @param classLoader ClassLoader para buscar recursos (default: context ou current)
     * @return AggoConfig se encontrado e válido, null caso contrário
     */
    fun loadFullConfigFromClasspath(classLoader: ClassLoader? = null): AggoConfig? {
        val loader = classLoader
            ?: Thread.currentThread().contextClassLoader
            ?: AggoPropertiesLoader::class.java.classLoader

        // Tenta YAML primeiro, depois properties
        val yamlFiles = listOf("application.yml", "application.yaml")
        val propertiesFile = "application.properties"

        // Tenta YAML
        for (yamlFile in yamlFiles) {
            loader.getResourceAsStream(yamlFile)?.use { stream ->
                val config = loadFullConfigFromYaml(stream)
                if (config != null) return config
            }
        }

        // Tenta properties
        loader.getResourceAsStream(propertiesFile)?.use { stream ->
            return loadFullConfigFromProperties(stream)
        }

        return null
    }

    /**
     * Carrega MirrorConfig a partir do classpath.
     *
     * @param classLoader ClassLoader para buscar recursos (default: context ou current)
     * @return MirrorConfig (sempre retorna, usa defaults se não encontrar)
     */
    fun loadMirrorConfigFromClasspath(classLoader: ClassLoader? = null): MirrorConfig {
        return loadFullConfigFromClasspath(classLoader)?.mirrorConfig ?: MirrorConfig()
    }

    /**
     * Carrega DbConfig de um InputStream de arquivo .properties
     */
    fun loadFromProperties(input: InputStream): DbConfig? {
        return loadFullConfigFromProperties(input)?.dbConfig
    }

    /**
     * Carrega configuração completa de um InputStream de arquivo .properties
     */
    fun loadFullConfigFromProperties(input: InputStream): AggoConfig? {
        val props = Properties()
        props.load(input)
        return buildFullConfig(props)
    }

    /**
     * Carrega DbConfig de um InputStream de arquivo .yml/.yaml
     * Requer SnakeYAML no classpath
     */
    fun loadFromYaml(input: InputStream): DbConfig? {
        return loadFullConfigFromYaml(input)?.dbConfig
    }

    /**
     * Carrega configuração completa de um InputStream de arquivo .yml/.yaml
     * Requer SnakeYAML no classpath
     */
    fun loadFullConfigFromYaml(input: InputStream): AggoConfig? {
        return try {
            val yaml = Class.forName("org.yaml.snakeyaml.Yaml")
                .getDeclaredConstructor()
                .newInstance()

            val loadMethod = yaml.javaClass.getMethod("load", InputStream::class.java)
            @Suppress("UNCHECKED_CAST")
            val data = loadMethod.invoke(yaml, input) as? Map<String, Any> ?: return null

            val flatProps = flattenYaml(data)
            val props = Properties()
            flatProps.forEach { (k, v) -> props.setProperty(k, v.toString()) }

            buildFullConfig(props)
        } catch (e: ClassNotFoundException) {
            // SnakeYAML não está no classpath - ignora silenciosamente
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Carrega DbConfig de um Map de propriedades
     * Útil para integração com outros frameworks
     */
    fun loadFromMap(properties: Map<String, Any?>): DbConfig? {
        return loadFullConfigFromMap(properties)?.dbConfig
    }

    /**
     * Carrega configuração completa de um Map de propriedades
     * Útil para integração com outros frameworks
     */
    fun loadFullConfigFromMap(properties: Map<String, Any?>): AggoConfig? {
        val props = Properties()
        properties.forEach { (k, v) -> v?.let { props.setProperty(k, it.toString()) } }
        return buildFullConfig(props)
    }

    /**
     * Transforma YAML hierárquico em propriedades flat (dot notation)
     */
    @Suppress("UNCHECKED_CAST")
    private fun flattenYaml(map: Map<String, Any>, prefix: String = ""): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        for ((key, value) in map) {
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"

            when (value) {
                is Map<*, *> -> result.putAll(flattenYaml(value as Map<String, Any>, fullKey))
                is List<*> -> {
                    value.forEachIndexed { index, item ->
                        if (item != null) {
                            result["$fullKey[$index]"] = item
                        }
                    }
                }
                else -> result[fullKey] = value
            }
        }

        return result
    }

    private fun buildFullConfig(props: Properties): AggoConfig? {
        val dbConfig = buildDbConfig(props) ?: return null
        val mirrorConfig = buildMirrorConfig(props)
        return AggoConfig(dbConfig, mirrorConfig)
    }

    private fun buildDbConfig(props: Properties): DbConfig? {
        val database = getProperty(props, PREFIX, "database") ?: return null
        val username = getProperty(props, PREFIX, "username") ?: getProperty(props, PREFIX, "user") ?: return null
        val password = getProperty(props, PREFIX, "password") ?: ""

        val host = getProperty(props, PREFIX, "host") ?: "localhost"

        // Determina o tipo de banco a partir do dialect
        val dialectStr = getProperty(props, PREFIX, "dialect")
            ?: getProperty(props, PREFIX, "database-type") // fallback para compatibilidade
            ?: "POSTGRESQL"

        val type = try {
            SupportedDatabases.valueOf(dialectStr.uppercase())
        } catch (e: Exception) {
            SupportedDatabases.POSTGRESQL
        }

        // Porta: usa a especificada ou a padrão do dialect
        val port = getProperty(props, PREFIX, "port")?.toIntOrNull() ?: type.defaultPort

        return DbConfig(
            database = database,
            host = host,
            port = port,
            user = username,
            password = password,
            type = type
        )
    }

    private fun buildMirrorConfig(props: Properties): MirrorConfig {
        // Try mirrors prefix first, then table-meta prefixes for Spring Boot compatibility
        val basePackage = getProperty(props, MIRRORS_PREFIX, "base-package")
            ?: getProperty(props, MIRRORS_PREFIX, "basePackage")
            ?: getProperty(props, TABLE_META_PREFIX, "base-package")
            ?: getProperty(props, TABLE_META_PREFIX, "basePackage")
            ?: getProperty(props, TABLE_META_PREFIX_ALT, "basePackage")
            ?: "generated.mirrors"

        val outputDir = getProperty(props, MIRRORS_PREFIX, "output-dir")
            ?: getProperty(props, MIRRORS_PREFIX, "outputDir")
            ?: getProperty(props, TABLE_META_PREFIX, "output-dir")
            ?: getProperty(props, TABLE_META_PREFIX, "outputDir")
            ?: getProperty(props, TABLE_META_PREFIX_ALT, "outputDir")
            ?: "src/main/kotlin"

        val schemaName = getProperty(props, MIRRORS_PREFIX, "schema-name")
            ?: getProperty(props, MIRRORS_PREFIX, "schemaName")
            ?: getProperty(props, TABLE_META_PREFIX, "schema-name")
            ?: getProperty(props, TABLE_META_PREFIX, "schemaName")
            ?: getProperty(props, TABLE_META_PREFIX_ALT, "schemaName")
            ?: "public"

        return MirrorConfig(
            basePackage = basePackage,
            outputDir = outputDir,
            schemaName = schemaName
        )
    }

    private fun getProperty(props: Properties, prefix: String, key: String): String? {
        // Tenta com prefixo completo
        props.getProperty("$prefix.$key")?.let { return it }

        // Tenta variações de case (kebab-case, camelCase)
        val kebabKey = key.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}-${it.groupValues[2].lowercase()}" }
        props.getProperty("$prefix.$kebabKey")?.let { return it }

        // Tenta camelCase de kebab-case
        val camelKey = key.replace(Regex("-([a-z])")) { it.groupValues[1].uppercase() }
        props.getProperty("$prefix.$camelKey")?.let { return it }

        return null
    }
}
