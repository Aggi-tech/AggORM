package com.aggitech.orm.migrations.meta

import com.aggitech.orm.migrations.dsl.ColumnType
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Classe base para metadados de tabela gerados.
 * Cada tabela e um object singleton com suas colunas.
 *
 * Exemplo de TableMeta gerado:
 * ```kotlin
 * object UsersTable : TableMeta("users") {
 *     val ID = uuid("id").primaryKey()
 *     val NAME = varchar("name", 100).notNull()
 *     val EMAIL = varchar("email", 255).notNull().unique()
 *     val CREATED_AT = timestamp("created_at").notNull().default("CURRENT_TIMESTAMP")
 * }
 * ```
 *
 * Uso em migrations:
 * ```kotlin
 * class V002_RemoveOldField : Migration() {
 *     override fun up() {
 *         table(UsersTable) {
 *             drop(UsersTable.OLD_FIELD)
 *         }
 *     }
 * }
 * ```
 */
abstract class TableMeta(
    val tableName: String,
    val schema: String = "public"
) {
    /**
     * Lista de todas as colunas da tabela.
     * Usa reflection para encontrar todas as propriedades do tipo ColumnMeta.
     */
    val columns: List<ColumnMeta> by lazy {
        this::class.memberProperties
            .filter { it.returnType.classifier == ColumnMeta::class }
            .mapNotNull { prop ->
                @Suppress("UNCHECKED_CAST")
                val column = (prop as KProperty1<TableMeta, ColumnMeta>).get(this)
                column.also { it.setTableName(tableName) }
            }
    }

    // ==================== Helpers para tipos comuns ====================
    // Esses metodos criam ColumnMeta que serao automaticamente descobertos via reflection

    fun uuid(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Uuid)

    fun varchar(name: String, length: Int = 255): ColumnMeta =
        ColumnMeta(name, ColumnType.Varchar(length))

    fun char(name: String, length: Int): ColumnMeta =
        ColumnMeta(name, ColumnType.Char(length))

    fun text(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Text)

    fun integer(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Integer)

    fun bigint(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.BigInteger)

    fun smallint(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.SmallInteger)

    fun boolean(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Boolean)

    fun timestamp(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Timestamp)

    fun date(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Date)

    fun time(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Time)

    fun decimal(name: String, precision: Int, scale: Int): ColumnMeta =
        ColumnMeta(name, ColumnType.Decimal(precision, scale))

    fun float(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Float)

    fun double(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Double)

    fun binary(name: String, length: Int? = null): ColumnMeta =
        ColumnMeta(name, ColumnType.Binary(length))

    fun blob(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Blob)

    fun json(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Json)

    fun jsonb(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Jsonb)

    /**
     * Encontra uma coluna pelo nome
     */
    fun findColumn(name: String): ColumnMeta? =
        columns.find { it.name == name }

    /**
     * Retorna a coluna primary key (ou null se nao houver)
     */
    fun primaryKeyColumn(): ColumnMeta? =
        columns.find { it.primaryKey }

    /**
     * Retorna todas as colunas que sao foreign keys
     */
    fun foreignKeyColumns(): List<ColumnMeta> =
        columns.filter { it.references != null }

    override fun toString(): String = "TableMeta($tableName, columns=${columns.map { it.name }})"
}
