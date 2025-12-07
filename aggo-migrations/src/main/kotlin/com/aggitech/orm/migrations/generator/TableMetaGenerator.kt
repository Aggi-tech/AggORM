package com.aggitech.orm.migrations.generator

import com.aggitech.orm.migrations.dsl.ColumnType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Gera codigo Kotlin para TableMeta baseado no schema do banco.
 * Os arquivos gerados podem ser usados em migrations de forma type-safe.
 *
 * Exemplo de uso:
 * ```kotlin
 * val generator = TableMetaGenerator(
 *     basePackage = "com.myapp.generated.tables",
 *     outputDir = Path.of("src/generated/tables")
 * )
 * val introspector = SchemaIntrospector(connection)
 * val schema = introspector.introspect()
 * val files = generator.generate(schema)
 * files.forEach { println("Generated: ${it.path}") }
 * ```
 *
 * Arquivo gerado exemplo (UsersTable.kt):
 * ```kotlin
 * package com.myapp.generated.tables
 *
 * import com.aggitech.orm.migrations.meta.TableMeta
 *
 * object UsersTable : TableMeta("users") {
 *     val ID = uuid("id").primaryKey()
 *     val NAME = varchar("name", 100).notNull()
 *     val EMAIL = varchar("email", 255).notNull().unique()
 * }
 * ```
 */
class TableMetaGenerator(
    private val basePackage: String = "generated.tables",
    private val outputDir: Path
) {

    /**
     * Gera arquivos TableMeta para todas as tabelas do schema
     */
    fun generate(schema: DatabaseSchema): List<GeneratedFile> {
        // Cria diretorio de saida se nao existir
        Files.createDirectories(outputDir)

        return schema.tables.map { table ->
            generateTableMeta(table)
        }
    }

    /**
     * Gera um arquivo TableMeta para uma tabela especifica
     */
    fun generateTableMeta(table: TableSchema): GeneratedFile {
        val className = table.name.toPascalCase() + "Table"
        val code = generateCode(className, table)

        val filePath = outputDir.resolve("$className.kt")
        Files.writeString(filePath, code)

        return GeneratedFile(
            path = filePath,
            content = code,
            tableName = table.name,
            className = className
        )
    }

    private fun generateCode(className: String, table: TableSchema): String {
        val sb = StringBuilder()

        // Package
        sb.appendLine("package $basePackage")
        sb.appendLine()

        // Imports
        sb.appendLine("import com.aggitech.orm.migrations.meta.TableMeta")
        sb.appendLine()

        // Comment
        sb.appendLine("/**")
        sb.appendLine(" * TableMeta gerado automaticamente para a tabela '${table.name}'.")
        sb.appendLine(" * Este arquivo e regenerado apos cada migration.")
        sb.appendLine(" *")
        sb.appendLine(" * NAO EDITE MANUALMENTE - suas alteracoes serao perdidas.")
        sb.appendLine(" */")

        // Object declaration
        sb.appendLine("object $className : TableMeta(\"${table.name}\", \"${table.schema}\") {")

        // Columns
        table.columns.forEach { col ->
            val constName = col.name.toScreamingSnakeCase()
            val typeCall = columnTypeToCall(col.type, col.name)
            val modifiers = buildModifiers(col, table)
            sb.appendLine("    val $constName = $typeCall$modifiers")
        }

        sb.appendLine("}")

        return sb.toString()
    }

    private fun columnTypeToCall(type: ColumnType, columnName: String): String {
        return when (type) {
            is ColumnType.Uuid -> "uuid(\"$columnName\")"
            is ColumnType.Varchar -> "varchar(\"$columnName\", ${type.length})"
            is ColumnType.Char -> "char(\"$columnName\", ${type.length})"
            is ColumnType.Text -> "text(\"$columnName\")"
            is ColumnType.Integer -> "integer(\"$columnName\")"
            is ColumnType.BigInteger -> "bigint(\"$columnName\")"
            is ColumnType.SmallInteger -> "smallint(\"$columnName\")"
            is ColumnType.Serial -> "serial(\"$columnName\")"
            is ColumnType.BigSerial -> "bigserial(\"$columnName\")"
            is ColumnType.Boolean -> "boolean(\"$columnName\")"
            is ColumnType.Timestamp -> "timestamp(\"$columnName\")"
            is ColumnType.TimestampTz -> "timestamptz(\"$columnName\")"
            is ColumnType.Date -> "date(\"$columnName\")"
            is ColumnType.Time -> "time(\"$columnName\")"
            is ColumnType.Decimal -> "decimal(\"$columnName\", ${type.precision}, ${type.scale})"
            is ColumnType.Float -> "float(\"$columnName\")"
            is ColumnType.Double -> "double(\"$columnName\")"
            is ColumnType.Binary -> if (type.length != null) "binary(\"$columnName\", ${type.length})" else "binary(\"$columnName\")"
            is ColumnType.Blob -> "blob(\"$columnName\")"
            is ColumnType.Json -> "json(\"$columnName\")"
            is ColumnType.Jsonb -> "jsonb(\"$columnName\")"
            is ColumnType.Enum -> {
                val values = type.values.joinToString(", ") { "\"$it\"" }
                "enum(\"$columnName\", \"${type.typeName}\", $values)"
            }
        }
    }

    private fun buildModifiers(col: ColumnSchema, table: TableSchema): String {
        val modifiers = mutableListOf<String>()

        if (col.primaryKey) {
            modifiers.add(".primaryKey()")
        }

        if (!col.nullable) {
            modifiers.add(".notNull()")
        }

        if (col.unique) {
            modifiers.add(".unique()")
        }

        if (col.autoIncrement) {
            modifiers.add(".autoIncrement()")
        }

        if (col.defaultValue != null) {
            val escapedDefault = col.defaultValue.replace("\"", "\\\"")
            modifiers.add(".default(\"$escapedDefault\")")
        }

        // Verifica se ha foreign key para esta coluna
        val fk = table.foreignKeys.find { it.columnName == col.name }
        if (fk != null) {
            val onDelete = if (fk.onDelete != null) ", \"${fk.onDelete}\"" else ""
            val onUpdate = if (fk.onUpdate != null) ", \"${fk.onUpdate}\"" else ""
            modifiers.add(".references(\"${fk.referencedTable}\", \"${fk.referencedColumn}\"$onDelete$onUpdate)")
        }

        return modifiers.joinToString("")
    }

    // ==================== String Extensions ====================

    /**
     * Converte snake_case para PascalCase
     * Exemplo: "user_profile" -> "UserProfile"
     */
    private fun String.toPascalCase(): String {
        return this.split("_")
            .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Converte snake_case para SCREAMING_SNAKE_CASE
     * Exemplo: "user_id" -> "USER_ID"
     */
    private fun String.toScreamingSnakeCase(): String {
        return this.uppercase()
    }
}

/**
 * Representa um arquivo gerado
 */
data class GeneratedFile(
    val path: Path,
    val content: String,
    val tableName: String,
    val className: String
)
