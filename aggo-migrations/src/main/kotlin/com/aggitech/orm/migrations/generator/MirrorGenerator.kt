package com.aggitech.orm.migrations.generator

import com.aggitech.orm.migrations.dsl.ColumnType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates Kotlin Mirror files from database schema.
 * Mirrors are auto-generated objects that extend Table and provide
 * type-safe column references for queries, inserts, updates, and deletes.
 *
 * Usage:
 * ```kotlin
 * val generator = MirrorGenerator(
 *     basePackage = "com.myapp.generated",
 *     outputDir = Path.of("src/generated/kotlin")
 * )
 * val introspector = SchemaIntrospector(connection)
 * val schema = introspector.introspect()
 * val files = generator.generate(schema)
 * files.forEach { println("Generated: ${it.path}") }
 * ```
 *
 * Generated file example (UserMirror.kt):
 * ```kotlin
 * package com.myapp.generated
 *
 * import com.aggitech.orm.table.Table
 *
 * /**
 *  * AUTO-GENERATED - DO NOT EDIT
 *  * Generated from table: users
 *  */
 * object UserMirror : Table("users", "public") {
 *     val ID = uuid("id").primaryKey().notNull()
 *     val NAME = varchar("name", 100).notNull()
 *     val EMAIL = varchar("email", 255).notNull().unique()
 *     val CREATED_AT = timestamptz("created_at").notNull()
 * }
 * ```
 *
 * Query usage:
 * ```kotlin
 * val users = select<UserMirror> {
 *     UserMirror.EMAIL like "%@example.com"
 * }.executeAs<User>()
 * ```
 */
class MirrorGenerator(
    private val basePackage: String = "generated",
    private val outputDir: Path
) {

    /**
     * Generates Mirror files for all tables in the schema
     */
    fun generate(schema: DatabaseSchema): List<GeneratedMirror> {
        Files.createDirectories(outputDir)

        return schema.tables.map { table ->
            generateMirror(table)
        }
    }

    /**
     * Generates a Mirror file for a specific table
     */
    fun generateMirror(table: TableSchema): GeneratedMirror {
        val className = table.name.toPascalCase() + "Mirror"
        val code = generateCode(className, table)

        val filePath = outputDir.resolve("$className.kt")
        Files.writeString(filePath, code)

        return GeneratedMirror(
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
        sb.appendLine("import com.aggitech.orm.table.Table")
        sb.appendLine()

        // Warning comment
        sb.appendLine("/**")
        sb.appendLine(" * AUTO-GENERATED - DO NOT EDIT")
        sb.appendLine(" * Generated from table: ${table.name}")
        sb.appendLine(" * Schema: ${table.schema}")
        sb.appendLine(" *")
        sb.appendLine(" * This file is regenerated after each migration.")
        sb.appendLine(" * Manual changes will be overwritten.")
        sb.appendLine(" */")

        // Object declaration
        sb.appendLine("object $className : Table(\"${table.name}\", \"${table.schema}\") {")

        // Columns
        table.columns.forEach { col ->
            val constName = col.name.toCamelCase()
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
            is ColumnType.Serial -> "serial(\"$columnName\")"
            is ColumnType.BigSerial -> "bigserial(\"$columnName\")"
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

        if (col.defaultValue != null) {
            val escapedDefault = col.defaultValue.replace("\"", "\\\"")
            modifiers.add(".default(\"$escapedDefault\")")
        }

        // Check for foreign key
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
     * Converts snake_case to PascalCase
     * Example: "user_profile" -> "UserProfile"
     */
    private fun String.toPascalCase(): String {
        return this.split("_")
            .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /**
     * Converte snake_case para camelCase
     * Exemplo: "first_name" -> "firstName"
     */
    private fun String.toCamelCase(): String {
        return this.split("_")
            .mapIndexed { index, part ->
                if (index == 0) part.lowercase()
                else part.replaceFirstChar { c -> c.uppercase() }
            }
            .joinToString("")
    }
}

/**
 * Represents a generated Mirror file
 */
data class GeneratedMirror(
    val path: Path,
    val content: String,
    val tableName: String,
    val className: String
)

// Backward compatibility aliases
@Deprecated("Use MirrorGenerator instead", ReplaceWith("MirrorGenerator"))
typealias BlueprintGenerator = MirrorGenerator

@Deprecated("Use GeneratedMirror instead", ReplaceWith("GeneratedMirror"))
typealias GeneratedBlueprint = GeneratedMirror
