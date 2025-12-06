package com.aggitech.orm.dsl

import com.aggitech.orm.query.model.*
import com.aggitech.orm.query.model.field.SelectField
import com.aggitech.orm.query.model.operand.Operand
import com.aggitech.orm.query.model.ordering.OrderDirection
import com.aggitech.orm.query.model.ordering.PropertyOrder
import com.aggitech.orm.query.model.predicate.ComparisonOperator
import com.aggitech.orm.query.model.predicate.Predicate
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

// ==================== Join Types ====================

enum class JoinType {
    INNER, LEFT, RIGHT, FULL
}

data class JoinClause(
    val type: JoinType,
    val targetTable: String,
    val condition: String,
    val parameters: List<Any?>
)

// ==================== Interfaces Comuns ====================

/**
 * Interface para builders que suportam cláusula WHERE (DRY)
 */
interface WhereableBuilder<T : Any> {
    val entityClass: KClass<T>
    var wherePredicate: Predicate?

    /**
     * Define a cláusula WHERE
     */
    fun where(block: WhereBuilder<T>.() -> Predicate) {
        val builder = WhereBuilder(entityClass)
        wherePredicate = builder.block()
    }
}

// ==================== DSL Builders ====================

/**
 * DSL entry point para criação de queries SELECT
 */
class SelectQueryBuilder<T : Any>(override val entityClass: KClass<T>) : WhereableBuilder<T> {
    private val fields = mutableListOf<SelectField>()
    private val joins = mutableListOf<JoinClause>()
    override var wherePredicate: Predicate? = null
    private val groupByFields = mutableListOf<SelectField>()
    private var havingPredicate: Predicate? = null
    private val orderByList = mutableListOf<PropertyOrder<*, *>>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null
    private var distinctFlag: Boolean = false

    /**
     * Define os campos a serem selecionados
     */
    fun select(block: SelectBuilder<T>.() -> Unit) {
        val builder = SelectBuilder(entityClass)
        builder.block()
        fields.addAll(builder.fields)
    }

    /**
     * Adiciona um campo individual
     */
    fun <R> field(property: KProperty1<T, R>, alias: String? = null) {
        fields.add(SelectField.Property(entityClass, property, alias))
    }

    // where() herdado de WhereableBuilder

    /**
     * Define GROUP BY
     */
    fun groupBy(vararg properties: KProperty1<T, *>) {
        properties.forEach { prop ->
            groupByFields.add(SelectField.Property(entityClass, prop))
        }
    }

    /**
     * Define HAVING
     */
    fun having(block: WhereBuilder<T>.() -> Predicate) {
        val builder = WhereBuilder(entityClass)
        havingPredicate = builder.block()
    }

    /**
     * Define ORDER BY
     */
    fun orderBy(block: OrderByBuilder<T>.() -> Unit) {
        val builder = OrderByBuilder(entityClass)
        builder.block()
        orderByList.addAll(builder.orderings)
    }

    /**
     * Define LIMIT
     */
    fun limit(value: Int) {
        limitValue = value
    }

    /**
     * Define OFFSET
     */
    fun offset(value: Int) {
        offsetValue = value
    }

    /**
     * Define DISTINCT
     */
    fun distinct() {
        distinctFlag = true
    }

    /**
     * Constrói a query SELECT
     */
    fun build(): SelectQuery<T> {
        return SelectQuery(
            from = entityClass,
            fields = fields,
            joins = joins,
            where = wherePredicate,
            groupBy = groupByFields,
            having = havingPredicate,
            orderBy = orderByList,
            limit = limitValue,
            offset = offsetValue,
            distinct = distinctFlag
        )
    }
}

/**
 * Builder para campos do SELECT
 */
class SelectBuilder<T : Any>(private val entityClass: KClass<T>) {
    val fields = mutableListOf<SelectField>()

    /**
     * Método auxiliar para criar campos de agregação (DRY)
     * @param function A função de agregação a ser usada
     * @param property A propriedade da entidade para agregar
     * @param alias O alias obrigatório para o resultado da agregação
     */
    private fun <R : Number> aggregate(
        function: com.aggitech.orm.query.model.field.AggregateFunction,
        property: KProperty1<T, R?>,
        alias: String
    ) {
        fields.add(
            SelectField.Aggregate(
                function = function,
                field = SelectField.Property(entityClass, property),
                alias = alias
            )
        )
    }

    /** Adiciona uma propriedade ao SELECT */
    operator fun <R> KProperty1<T, R>.unaryPlus() {
        fields.add(SelectField.Property(entityClass, this))
    }

    /** Adiciona uma propriedade com alias */
    infix fun <R> KProperty1<T, R>.alias(alias: String) {
        fields.add(SelectField.Property(entityClass, this, alias))
    }

    /** Adiciona todos os campos (SELECT *) */
    fun all() {
        fields.add(SelectField.All)
    }

    /**
     * COUNT de uma propriedade específica
     * @param property A propriedade a ser contada (valores não-nulos)
     * @param alias Alias obrigatório para o resultado
     */
    fun <R> count(property: KProperty1<T, R>, alias: String) {
        fields.add(
            SelectField.Aggregate(
                function = com.aggitech.orm.query.model.field.AggregateFunction.COUNT,
                field = SelectField.Property(entityClass, property),
                alias = alias
            )
        )
    }

    /**
     * COUNT(*) - conta todas as linhas
     * @param alias Alias obrigatório para o resultado
     */
    fun countAll(alias: String) {
        fields.add(
            SelectField.Aggregate(
                function = com.aggitech.orm.query.model.field.AggregateFunction.COUNT,
                field = SelectField.All,
                alias = alias
            )
        )
    }

    /**
     * COUNT(DISTINCT column) - conta valores únicos
     * @param property A propriedade a ser contada (valores únicos não-nulos)
     * @param alias Alias obrigatório para o resultado
     */
    fun <R> countDistinct(property: KProperty1<T, R>, alias: String) {
        fields.add(
            SelectField.Aggregate(
                function = com.aggitech.orm.query.model.field.AggregateFunction.COUNT_DISTINCT,
                field = SelectField.Property(entityClass, property),
                alias = alias
            )
        )
    }

    /**
     * SUM - soma valores numéricos
     * @param property A propriedade numérica a ser somada
     * @param alias Alias obrigatório para o resultado
     */
    fun <R : Number> sum(property: KProperty1<T, R?>, alias: String) =
        aggregate(com.aggitech.orm.query.model.field.AggregateFunction.SUM, property, alias)

    /**
     * AVG - calcula a média de valores numéricos
     * @param property A propriedade numérica para calcular a média
     * @param alias Alias obrigatório para o resultado
     */
    fun <R : Number> avg(property: KProperty1<T, R?>, alias: String) =
        aggregate(com.aggitech.orm.query.model.field.AggregateFunction.AVG, property, alias)

    /**
     * MAX - retorna o valor máximo
     * @param property A propriedade numérica para encontrar o máximo
     * @param alias Alias obrigatório para o resultado
     */
    fun <R : Number> max(property: KProperty1<T, R?>, alias: String) =
        aggregate(com.aggitech.orm.query.model.field.AggregateFunction.MAX, property, alias)

    /**
     * MIN - retorna o valor mínimo
     * @param property A propriedade numérica para encontrar o mínimo
     * @param alias Alias obrigatório para o resultado
     */
    fun <R : Number> min(property: KProperty1<T, R?>, alias: String) =
        aggregate(com.aggitech.orm.query.model.field.AggregateFunction.MIN, property, alias)
}

/**
 * Builder para cláusula WHERE
 */
class WhereBuilder<T : Any>(private val entityClass: KClass<T>) {

    /**
     * Método auxiliar para criar predicados de comparação (DRY)
     */
    private fun <R> comparison(
        property: KProperty1<T, R>,
        operator: ComparisonOperator,
        value: R
    ): Predicate = Predicate.Comparison(
        left = Operand.Property(entityClass, property),
        operator = operator,
        right = Operand.Literal(value)
    )

    /** Operador de igualdade (=) */
    infix fun <R> KProperty1<T, R>.eq(value: R): Predicate = comparison(this, ComparisonOperator.EQ, value)

    /** Operador de diferença (!=) */
    infix fun <R> KProperty1<T, R>.ne(value: R): Predicate = comparison(this, ComparisonOperator.NE, value)

    /** Operador maior que (>) */
    infix fun <R> KProperty1<T, R>.gt(value: R): Predicate = comparison(this, ComparisonOperator.GT, value)

    /** Operador maior ou igual (>=) */
    infix fun <R> KProperty1<T, R>.gte(value: R): Predicate = comparison(this, ComparisonOperator.GTE, value)

    /** Operador menor que (<) */
    infix fun <R> KProperty1<T, R>.lt(value: R): Predicate = comparison(this, ComparisonOperator.LT, value)

    /** Operador menor ou igual (<=) */
    infix fun <R> KProperty1<T, R>.lte(value: R): Predicate = comparison(this, ComparisonOperator.LTE, value)

    /**
     * LIKE
     */
    infix fun KProperty1<T, String>.like(pattern: String): Predicate {
        return Predicate.Like(
            operand = Operand.Property(entityClass, this),
            pattern = pattern
        )
    }

    /**
     * NOT LIKE
     */
    infix fun KProperty1<T, String>.notLike(pattern: String): Predicate {
        return Predicate.NotLike(
            operand = Operand.Property(entityClass, this),
            pattern = pattern
        )
    }

    /**
     * IN
     */
    infix fun <R> KProperty1<T, R>.inList(values: List<R>): Predicate {
        return Predicate.In(
            operand = Operand.Property(entityClass, this),
            values = values
        )
    }

    /**
     * NOT IN
     */
    infix fun <R> KProperty1<T, R>.notInList(values: List<R>): Predicate {
        return Predicate.NotIn(
            operand = Operand.Property(entityClass, this),
            values = values
        )
    }

    /**
     * IS NULL
     */
    fun <R> KProperty1<T, R>.isNull(): Predicate {
        return Predicate.IsNull(Operand.Property(entityClass, this))
    }

    /**
     * IS NOT NULL
     */
    fun <R> KProperty1<T, R>.isNotNull(): Predicate {
        return Predicate.IsNotNull(Operand.Property(entityClass, this))
    }

    /**
     * BETWEEN
     */
    fun <R : Comparable<R>> KProperty1<T, R>.between(lower: R, upper: R): Predicate {
        return Predicate.Between(
            operand = Operand.Property(entityClass, this),
            lower = lower,
            upper = upper
        )
    }

    /**
     * AND lógico
     */
    infix fun Predicate.and(other: Predicate): Predicate {
        return Predicate.And(this, other)
    }

    /**
     * OR lógico
     */
    infix fun Predicate.or(other: Predicate): Predicate {
        return Predicate.Or(this, other)
    }

    /**
     * NOT lógico
     */
    fun not(predicate: Predicate): Predicate {
        return Predicate.Not(predicate)
    }
}

/**
 * Builder para ORDER BY
 */
class OrderByBuilder<T : Any>(private val entityClass: KClass<T>) {
    val orderings = mutableListOf<PropertyOrder<*, *>>()

    /**
     * Adiciona ordenação ASC
     */
    fun <R> KProperty1<T, R>.asc() {
        orderings.add(PropertyOrder(entityClass, this, OrderDirection.ASC))
    }

    /**
     * Adiciona ordenação DESC
     */
    fun <R> KProperty1<T, R>.desc() {
        orderings.add(PropertyOrder(entityClass, this, OrderDirection.DESC))
    }
}

/**
 * Função de extensão para criar queries SELECT
 */
inline fun <reified T : Any> select(block: SelectQueryBuilder<T>.() -> Unit): SelectQuery<T> {
    val builder = SelectQueryBuilder(T::class)
    builder.block()
    return builder.build()
}

/**
 * DSL para INSERT
 */
class InsertQueryBuilder<T : Any>(private val entityClass: KClass<T>) {
    private val values = mutableMapOf<String, Any?>()

    /**
     * Define um valor para uma coluna
     */
    infix fun <R> KProperty1<T, R>.to(value: R?) {
        values[this.name] = value
    }

    /**
     * Constrói a query INSERT
     */
    fun build(): InsertQuery<T> {
        return InsertQuery(into = entityClass, values = values)
    }
}

/**
 * Função de extensão para criar queries INSERT
 */
inline fun <reified T : Any> insert(block: InsertQueryBuilder<T>.() -> Unit): InsertQuery<T> {
    val builder = InsertQueryBuilder(T::class)
    builder.block()
    return builder.build()
}

/**
 * Função para INSERT de entidade
 */
inline fun <reified T : Any> insert(entity: T): InsertQuery<T> {
    return InsertQuery(into = T::class, entity = entity)
}

/**
 * DSL para UPDATE
 */
class UpdateQueryBuilder<T : Any>(override val entityClass: KClass<T>) : WhereableBuilder<T> {
    private val updates = mutableMapOf<String, Any?>()
    override var wherePredicate: Predicate? = null

    /**
     * Define um valor para atualizar
     */
    infix fun <R> KProperty1<T, R>.to(value: R?) {
        updates[this.name] = value
    }

    // where() herdado de WhereableBuilder

    /**
     * Constrói a query UPDATE
     */
    fun build(): UpdateQuery<T> {
        return UpdateQuery(
            table = entityClass,
            updates = updates,
            where = wherePredicate
        )
    }
}

/**
 * Função de extensão para criar queries UPDATE
 */
inline fun <reified T : Any> update(block: UpdateQueryBuilder<T>.() -> Unit): UpdateQuery<T> {
    val builder = UpdateQueryBuilder(T::class)
    builder.block()
    return builder.build()
}

/**
 * DSL para DELETE
 */
class DeleteQueryBuilder<T : Any>(override val entityClass: KClass<T>) : WhereableBuilder<T> {
    override var wherePredicate: Predicate? = null

    // where() herdado de WhereableBuilder

    /**
     * Constrói a query DELETE
     */
    fun build(): DeleteQuery<T> {
        return DeleteQuery(
            from = entityClass,
            where = wherePredicate
        )
    }
}

/**
 * Função de extensão para criar queries DELETE
 */
inline fun <reified T : Any> delete(block: DeleteQueryBuilder<T>.() -> Unit): DeleteQuery<T> {
    val builder = DeleteQueryBuilder(T::class)
    builder.block()
    return builder.build()
}
