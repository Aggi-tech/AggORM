package com.aggitech.orm.migrations.meta

import com.aggitech.orm.migrations.dsl.ColumnType
import com.aggitech.orm.table.Table
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Classe base para metadados de tabela gerados.
 * Cada tabela e um object singleton com suas colunas.
 *
 * Estende [Table] para compatibilidade com as funções de query DSL.
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
 * Uso em queries:
 * ```kotlin
 * import com.aggitech.orm.table.select
 * import com.aggitech.orm.table.insert
 * import com.aggitech.orm.table.update
 * import com.aggitech.orm.table.delete
 *
 * // SELECT
 * select(UsersTable) {
 *     UsersTable.NAME eq "John"
 * }.executeAs<User>()
 *
 * // INSERT
 * insert(UsersTable) {
 *     UsersTable.NAME to "John"
 *     UsersTable.EMAIL to "john@example.com"
 * }.execute()
 *
 * // UPDATE
 * update(UsersTable) {
 *     UsersTable.NAME to "Jane"
 *     where { UsersTable.ID eq userId }
 * }.execute()
 *
 * // DELETE
 * delete(UsersTable) {
 *     UsersTable.ID eq userId
 * }.execute()
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
    tableName: String,
    schema: String = "public"
) : Table(tableName, schema) {
    /**
     * Lista de todas as colunas da tabela como ColumnMeta.
     * Usa reflection para encontrar todas as propriedades do tipo ColumnMeta.
     */
    val columnsMeta: List<ColumnMeta> by lazy {
        this::class.memberProperties
            .filter { it.returnType.classifier == ColumnMeta::class }
            .mapNotNull { prop ->
                @Suppress("UNCHECKED_CAST")
                (prop as KProperty1<TableMeta, ColumnMeta>).get(this)
            }
    }

    // ==================== Helpers para tipos comuns ====================
    // Esses metodos criam ColumnMeta que serao automaticamente descobertos via reflection
    // Override dos métodos de Table para retornar ColumnMeta

    override fun uuid(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Uuid, tableName)

    override fun varchar(name: String, length: Int): ColumnMeta =
        ColumnMeta(name, ColumnType.Varchar(length), tableName)

    override fun char(name: String, length: Int): ColumnMeta =
        ColumnMeta(name, ColumnType.Char(length), tableName)

    override fun text(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Text, tableName)

    override fun integer(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Integer, tableName)

    override fun bigint(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.BigInteger, tableName)

    override fun smallint(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.SmallInteger, tableName)

    override fun boolean(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Boolean, tableName)

    override fun timestamp(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Timestamp, tableName)

    override fun date(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Date, tableName)

    override fun time(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Time, tableName)

    override fun decimal(name: String, precision: Int, scale: Int): ColumnMeta =
        ColumnMeta(name, ColumnType.Decimal(precision, scale), tableName)

    override fun float(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Float, tableName)

    override fun double(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Double, tableName)

    override fun binary(name: String, length: Int?): ColumnMeta =
        ColumnMeta(name, ColumnType.Binary(length), tableName)

    override fun blob(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Blob, tableName)

    override fun json(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Json, tableName)

    override fun jsonb(name: String): ColumnMeta =
        ColumnMeta(name, ColumnType.Jsonb, tableName)

    /**
     * Encontra uma coluna pelo nome
     */
    fun findColumnMeta(name: String): ColumnMeta? =
        columnsMeta.find { it.name == name }

    /**
     * Retorna a coluna primary key (ou null se nao houver)
     */
    fun primaryKeyColumnMeta(): ColumnMeta? =
        columnsMeta.find { it.primaryKey }

    /**
     * Retorna todas as colunas que sao foreign keys
     */
    fun foreignKeyColumns(): List<ColumnMeta> =
        columnsMeta.filter { it.references != null }

    override fun toString(): String = "TableMeta($tableName, columns=${columnsMeta.map { it.name }})"
}
