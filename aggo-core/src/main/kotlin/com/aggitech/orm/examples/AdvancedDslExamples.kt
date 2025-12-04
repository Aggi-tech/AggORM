package com.aggitech.orm.examples

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.entities.annotations.*
import com.aggitech.orm.enums.PostgresDialect
import com.aggitech.orm.query.model.SelectQuery
import com.aggitech.orm.query.model.field.SelectField
import com.aggitech.orm.query.model.operand.Operand
import com.aggitech.orm.query.model.ordering.OrderDirection
import com.aggitech.orm.query.model.ordering.PropertyOrder
import com.aggitech.orm.query.model.predicate.ComparisonOperator
import com.aggitech.orm.query.model.predicate.Predicate
import com.aggitech.orm.sql.renderer.SelectRenderer

/**
 * Exemplos avançados da DSL do AggORM
 * Demonstrando o uso de sealed classes, EntityRegistry e QueryRenderer
 */

// Entities are now in ExampleEntities.kt to avoid redeclaration errors

// ==================== Exemplo 1: Query Básica ====================

fun example1_BasicQuery() {
    println("=== Exemplo 1: Query Básica ===\n")

    // Construir uma query usando sealed classes diretamente
    val query = SelectQuery(
        from = User::class,
        fields = listOf(
            SelectField.Property(User::class, User::name),
            SelectField.Property(User::class, User::email)
        ),
        where = Predicate.Comparison(
            left = Operand.Property(User::class, User::age),
            operator = ComparisonOperator.GTE,
            right = Operand.Literal(18)
        ),
        orderBy = listOf(
            PropertyOrder(User::class, User::name, OrderDirection.ASC)
        ),
        limit = 10
    )

    // Renderizar para SQL
    val renderer = SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL gerado:")
    println(rendered.sql)
    println("\nParâmetros:")
    rendered.parameters.forEachIndexed { index, param ->
        println("  $${index + 1} = $param")
    }
    println("\nSQL debug (com valores):")
    println(rendered.toDebugString())
    println()
}

// ==================== Exemplo 2: Query com Predicados Compostos ====================

fun example2_ComplexPredicates() {
    println("=== Exemplo 2: Predicados Compostos ===\n")

    // (age >= 18 AND age <= 65) AND email LIKE '%@gmail.com'
    val predicate = Predicate.And(
        left = Predicate.And(
            left = Predicate.Comparison(
                left = Operand.Property(User::class, User::age),
                operator = ComparisonOperator.GTE,
                right = Operand.Literal(18)
            ),
            right = Predicate.Comparison(
                left = Operand.Property(User::class, User::age),
                operator = ComparisonOperator.LTE,
                right = Operand.Literal(65)
            )
        ),
        right = Predicate.Like(
            operand = Operand.Property(User::class, User::email),
            pattern = "%@gmail.com"
        )
    )

    val query = SelectQuery(
        from = User::class,
        fields = emptyList(), // SELECT *
        where = predicate
    )

    val renderer = SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL gerado:")
    println(rendered.sql)
    println("\nParâmetros:")
    rendered.parameters.forEachIndexed { index, param ->
        println("  $${index + 1} = $param")
    }
    println()
}

// ==================== Exemplo 3: IN Predicate ====================

fun example3_InPredicate() {
    println("=== Exemplo 3: IN Predicate ===\n")

    val predicate = Predicate.In(
        operand = Operand.Property(User::class, User::cityId),
        values = listOf(1L, 2L, 3L, 5L, 8L)
    )

    val query = SelectQuery(
        from = User::class,
        fields = emptyList(),
        where = predicate,
        orderBy = listOf(
            PropertyOrder(User::class, User::name, OrderDirection.ASC)
        )
    )

    val renderer = SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL gerado:")
    println(rendered.sql)
    println("\nParâmetros: ${rendered.parameters}")
    println()
}

// ==================== Exemplo 4: BETWEEN Predicate ====================

fun example4_BetweenPredicate() {
    println("=== Exemplo 4: BETWEEN Predicate ===\n")

    val predicate = Predicate.Between(
        operand = Operand.Property(User::class, User::age),
        lower = 25,
        upper = 35
    )

    val query = SelectQuery(
        from = User::class,
        fields = emptyList(),
        where = predicate,
        distinct = true
    )

    val renderer = SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL gerado:")
    println(rendered.sql)
    println("\nParâmetros: ${rendered.parameters}")
    println()
}

// ==================== Exemplo 5: Aggregate Functions ====================

fun example5_AggregateFunctions() {
    println("=== Exemplo 5: Funções de Agregação ===\n")

    val query = SelectQuery(
        from = User::class,
        fields = listOf(
            SelectField.Aggregate(
                function = com.aggitech.orm.query.model.field.AggregateFunction.COUNT,
                field = SelectField.All,
                alias = "total_users"
            ),
            SelectField.Aggregate(
                function = com.aggitech.orm.query.model.field.AggregateFunction.AVG,
                field = SelectField.Property(User::class, User::age),
                alias = "average_age"
            )
        )
    )

    val renderer = SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL gerado:")
    println(rendered.sql)
    println()
}

// ==================== Exemplo 6: IS NULL / IS NOT NULL ====================

fun example6_NullPredicates() {
    println("=== Exemplo 6: NULL Predicates ===\n")

    // Usuários sem cidade (cityId IS NULL)
    val nullPredicate = Predicate.IsNull(
        operand = Operand.Property(User::class, User::cityId)
    )

    val query1 = SelectQuery(
        from = User::class,
        fields = emptyList(),
        where = nullPredicate
    )

    // Usuários com cidade (cityId IS NOT NULL)
    val notNullPredicate = Predicate.IsNotNull(
        operand = Operand.Property(User::class, User::cityId)
    )

    val query2 = SelectQuery(
        from = User::class,
        fields = emptyList(),
        where = notNullPredicate
    )

    val renderer = SelectRenderer(PostgresDialect)

    println("Query 1 (IS NULL):")
    println(renderer.render(query1).sql)
    println()

    println("Query 2 (IS NOT NULL):")
    println(renderer.render(query2).sql)
    println()
}

// ==================== Exemplo 7: EntityRegistry Snake Case ====================

fun example7_EntityRegistry() {
    println("=== Exemplo 7: EntityRegistry Snake Case Conversion ===\n")

    // Demonstrar conversão automática de nomes
    println("Conversões de tabelas:")
    println("  User -> ${EntityRegistry.resolveTable(User::class)}")
    println("  City -> ${EntityRegistry.resolveTable(City::class)}")

    println("\nConversões de colunas:")
    println("  User::cityId -> ${EntityRegistry.resolveColumn(User::cityId)}")
    println("  User::name -> ${EntityRegistry.resolveColumn(User::name)}")
    println("  User::email -> ${EntityRegistry.resolveColumn(User::email)}")

    println("\nExemplo com camelCase mais complexo:")
    data class HTTPSConnection(val connectionURL: String, val isSecure: Boolean)
    // connectionURL -> connection_url
    // isSecure -> is_secure
    println()
}

// ==================== Exemplo 8: NOT e OR Predicates ====================

fun example8_NotAndOrPredicates() {
    println("=== Exemplo 8: NOT e OR Predicates ===\n")

    // NOT (email LIKE '%@gmail.com')
    val notPredicate = Predicate.Not(
        predicate = Predicate.Like(
            operand = Operand.Property(User::class, User::email),
            pattern = "%@gmail.com"
        )
    )

    // age < 18 OR age > 65
    val orPredicate = Predicate.Or(
        left = Predicate.Comparison(
            left = Operand.Property(User::class, User::age),
            operator = ComparisonOperator.LT,
            right = Operand.Literal(18)
        ),
        right = Predicate.Comparison(
            left = Operand.Property(User::class, User::age),
            operator = ComparisonOperator.GT,
            right = Operand.Literal(65)
        )
    )

    val renderer = SelectRenderer(PostgresDialect)

    println("Query 1 (NOT):")
    val query1 = SelectQuery(from = User::class, where = notPredicate)
    println(renderer.render(query1).sql)
    println()

    println("Query 2 (OR):")
    val query2 = SelectQuery(from = User::class, where = orPredicate)
    println(renderer.render(query2).sql)
    println()
}

// ==================== Main ====================

fun main() {
    example1_BasicQuery()
    example2_ComplexPredicates()
    example3_InPredicate()
    example4_BetweenPredicate()
    example5_AggregateFunctions()
    example6_NullPredicates()
    example7_EntityRegistry()
    example8_NotAndOrPredicates()

    println("=== Todos os exemplos executados com sucesso! ===")
}
