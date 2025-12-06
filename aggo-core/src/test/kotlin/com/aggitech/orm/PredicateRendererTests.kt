package com.aggitech.orm

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.enums.PostgresDialect
import com.aggitech.orm.query.model.operand.Operand
import com.aggitech.orm.query.model.predicate.ComparisonOperator
import com.aggitech.orm.query.model.predicate.Predicate
import com.aggitech.orm.sql.context.RenderContext
import com.aggitech.orm.sql.renderer.PredicateRenderer
import kotlin.test.*

/**
 * Testes unitários para PredicateRenderer
 */
class PredicateRendererTests {

    private val renderer = PredicateRenderer(PostgresDialect)

    @BeforeTest
    fun setup() {
        EntityRegistry.clearCache()
    }

    @Test
    fun `should render simple comparison EQ`() {
        val predicate = Predicate.Comparison(
            left = Operand.Property(User::class, User::name),
            operator = ComparisonOperator.EQ,
            right = Operand.Literal("John")
        )
        val ctx = RenderContext(PostgresDialect)
        val result = renderer.render(predicate, ctx)
        assertEquals("\"user\".\"name\" = ?", result)
        assertEquals(1, ctx.parameters.size)
    }

    @Test
    fun `should render all comparison operators`() {
        val ctx = RenderContext(PostgresDialect)

        val operators = listOf(
            ComparisonOperator.EQ to "=",
            ComparisonOperator.NE to "!=",
            ComparisonOperator.GT to ">",
            ComparisonOperator.GTE to ">=",
            ComparisonOperator.LT to "<",
            ComparisonOperator.LTE to "<="
        )

        operators.forEach { (op, symbol) ->
            val predicate = Predicate.Comparison(
                left = Operand.Property(User::class, User::age),
                operator = op,
                right = Operand.Literal(18)
            )
            val result = renderer.render(predicate, ctx)
            assertTrue(result.contains(symbol), "Expected $symbol in result for $op")
        }
    }

    @Test
    fun `should render AND predicate`() {
        val predicate = Predicate.And(
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
        )
        val ctx = RenderContext(PostgresDialect)
        val result = renderer.render(predicate, ctx)
        assertEquals("(\"user\".\"age\" >= ? AND \"user\".\"age\" <= ?)", result)
    }

    @Test
    fun `should render OR predicate`() {
        val predicate = Predicate.Or(
            left = Predicate.Comparison(
                left = Operand.Property(User::class, User::name),
                operator = ComparisonOperator.EQ,
                right = Operand.Literal("John")
            ),
            right = Predicate.Comparison(
                left = Operand.Property(User::class, User::name),
                operator = ComparisonOperator.EQ,
                right = Operand.Literal("Jane")
            )
        )
        val ctx = RenderContext(PostgresDialect)
        val result = renderer.render(predicate, ctx)
        assertEquals("(\"user\".\"name\" = ? OR \"user\".\"name\" = ?)", result)
    }

    @Test
    fun `should render NOT predicate`() {
        val predicate = Predicate.Not(
            Predicate.Comparison(
                left = Operand.Property(User::class, User::age),
                operator = ComparisonOperator.LT,
                right = Operand.Literal(18)
            )
        )
        val ctx = RenderContext(PostgresDialect)
        val result = renderer.render(predicate, ctx)
        assertEquals("NOT (\"user\".\"age\" < ?)", result)
    }

    @Test
    fun `should render IN predicate`() {
        val predicate = Predicate.In<Long>(
            operand = Operand.Property(User::class, User::cityId),
            values = listOf(1L, 2L, 3L)
        )
        val ctx = RenderContext(PostgresDialect)
        val result = renderer.render(predicate, ctx)
        assertEquals("\"user\".\"city_id\" IN (?, ?, ?)", result)
    }

    @Test
    fun `should render BETWEEN predicate`() {
        val predicate = Predicate.Between<Int>(
            operand = Operand.Property(User::class, User::age),
            lower = 18,
            upper = 65
        )
        val ctx = RenderContext(PostgresDialect)
        val result = renderer.render(predicate, ctx)
        assertEquals("\"user\".\"age\" BETWEEN ? AND ?", result)
    }

    @Test
    fun `should render IS NULL predicate`() {
        val predicate = Predicate.IsNull(
            operand = Operand.Property(User::class, User::cityId)
        )
        val ctx = RenderContext(PostgresDialect)
        val result = renderer.render(predicate, ctx)
        assertEquals("\"user\".\"city_id\" IS NULL", result)
    }

    @Test
    fun `should render LIKE predicate`() {
        val predicate = Predicate.Like(
            operand = Operand.Property(User::class, User::email),
            pattern = "%@gmail.com"
        )
        val ctx = RenderContext(PostgresDialect)
        val result = renderer.render(predicate, ctx)
        assertEquals("\"user\".\"email\" LIKE ?", result)
    }
}
