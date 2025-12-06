package com.aggitech.orm.config

import com.aggitech.orm.enums.SupportedDatabases
import java.io.InputStream
import java.util.Properties

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
 * Exemplo application.properties:
 * ```
 * aggo.orm.database=mydb
 * aggo.orm.host=localhost
 * aggo.orm.username=user
 * aggo.orm.password=pass
 * aggo.orm.dialect=POSTGRESQL
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
 * ```
 */
object AggoPropertiesLoader {

    private const val PREFIX = "aggo.orm"

    /**
     * Carrega DbConfig a partir do classpath
     * Procura por application.yml, application.yaml, application.properties nessa ordem
     *
     * @param classLoader ClassLoader para buscar recursos (default: context ou current)
     * @return DbConfig se encontrado e válido, null caso contrário
     */
    fun loadFromClasspath(classLoader: ClassLoader? = null): DbConfig? {
        val loader = classLoader
            ?: Thread.currentThread().contextClassLoader
            ?: AggoPropertiesLoader::class.java.classLoader

        // Tenta YAML primeiro, depois properties
        val yamlFiles = listOf("application.yml", "application.yaml")
        val propertiesFile = "application.properties"

        // Tenta YAML
        for (yamlFile in yamlFiles) {
            loader.getResourceAsStream(yamlFile)?.use { stream ->
                val config = loadFromYaml(stream)
                if (config != null) return config
            }
        }

        // Tenta properties
        loader.getResourceAsStream(propertiesFile)?.use { stream ->
            return loadFromProperties(stream)
        }

        return null
    }

    /**
     * Carrega DbConfig de um InputStream de arquivo .properties
     */
    fun loadFromProperties(input: InputStream): DbConfig? {
        val props = Properties()
        props.load(input)
        return buildConfig(props)
    }

    /**
     * Carrega DbConfig de um InputStream de arquivo .yml/.yaml
     * Requer SnakeYAML no classpath
     */
    fun loadFromYaml(input: InputStream): DbConfig? {
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

            buildConfig(props)
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
        val props = Properties()
        properties.forEach { (k, v) -> v?.let { props.setProperty(k, it.toString()) } }
        return buildConfig(props)
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

    private fun buildConfig(props: Properties): DbConfig? {
        val database = getProperty(props, "database") ?: return null
        val username = getProperty(props, "username") ?: getProperty(props, "user") ?: return null
        val password = getProperty(props, "password") ?: ""

        val host = getProperty(props, "host") ?: "localhost"

        // Determina o tipo de banco a partir do dialect
        val dialectStr = getProperty(props, "dialect")
            ?: getProperty(props, "database-type") // fallback para compatibilidade
            ?: "POSTGRESQL"

        val type = try {
            SupportedDatabases.valueOf(dialectStr.uppercase())
        } catch (e: Exception) {
            SupportedDatabases.POSTGRESQL
        }

        // Porta: usa a especificada ou a padrão do dialect
        val port = getProperty(props, "port")?.toIntOrNull() ?: type.defaultPort

        return DbConfig(
            database = database,
            host = host,
            port = port,
            user = username,
            password = password,
            type = type
        )
    }

    private fun getProperty(props: Properties, key: String): String? {
        // Tenta com prefixo completo
        props.getProperty("$PREFIX.$key")?.let { return it }

        // Tenta variações de case (kebab-case, camelCase)
        val kebabKey = key.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}-${it.groupValues[2].lowercase()}" }
        props.getProperty("$PREFIX.$kebabKey")?.let { return it }

        return null
    }
}
