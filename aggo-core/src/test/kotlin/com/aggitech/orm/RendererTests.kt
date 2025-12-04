package com.aggitech.orm

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.enums.PostgresDialect
import com.aggitech.orm.query.model.*
import com.aggitech.orm.query.model.field.AggregateFunction
import com.aggitech.orm.query.model.field.SelectField
import com.aggitech.orm.query.model.operand.Operand
import com.aggitech.orm.query.model.ordering.OrderDirection
import com.aggitech.orm.query.model.ordering.PropertyOrder
import com.aggitech.orm.query.model.predicate.ComparisonOperator
import com.aggitech.orm.query.model.predicate.Predicate
import com.aggitech.orm.sql.renderer.*
import kotlin.test.*

// Entidades de teste
data class User(
    val id: Long? = null,
    val name: String,
    val email: String,
    val age: Int,
    val cityId: Long? = null
)

data class City(
    val id: Long? = null,
    val name: String
)

class RendererTests {

    @BeforeTest
    fun setup() {
        EntityRegistry.clearCache()
    }

    @Test
    fun `test simple SELECT query`() {
        val query = SelectQuery(
            from = User::class,
            fields = listOf(
                SelectField.Property(User::class, User::name),
                SelectField.Property(User::class, User::email)
            )
        )

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT user.name, user.email FROM user", rendered.sql)
        assertTrue(rendered.parameters.isEmpty())
    }

    @Test
    fun `test SELECT with WHERE`() {
        val query = SelectQuery(
            from = User::class,
            where = Predicate.Comparison(
                left = Operand.Property(User::class, User::age),
                operator = ComparisonOperator.GTE,
                right = Operand.Literal(18)
            )
        )

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.age >= ?", rendered.sql)
        assertEquals(listOf(18), rendered.parameters)
    }

    @Test
    fun `test SELECT with complex WHERE`() {
        val predicate = Predicate.And(
            left = Predicate.Comparison(
                left = Operand.Property(User::class, User::age),
                operator = ComparisonOperator.GTE,
                right = Operand.Literal(18)
            ),
            right = Predicate.Like(
                operand = Operand.Property(User::class, User::email),
                pattern = "%@gmail.com"
            )
        )

        val query = SelectQuery(from = User::class, where = predicate)

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE (user.age >= ? AND user.email LIKE ?)", rendered.sql)
        assertEquals(listOf(18, "%@gmail.com"), rendered.parameters)
    }

    @Test
    fun `test SELECT with IN predicate`() {
        val predicate = Predicate.In(
            operand = Operand.Property(User::class, User::cityId),
            values = listOf(1L, 2L, 3L)
        )

        val query = SelectQuery(from = User::class, where = predicate)

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.city_id IN (?, ?, ?)", rendered.sql)
        assertEquals(listOf(1L, 2L, 3L), rendered.parameters)
    }

    @Test
    fun `test SELECT with aggregate functions`() {
        val query = SelectQuery(
            from = User::class,
            fields = listOf(
                SelectField.Aggregate(
                    function = AggregateFunction.COUNT,
                    field = SelectField.All,
                    alias = "total"
                ),
                SelectField.Aggregate(
                    function = AggregateFunction.AVG,
                    field = SelectField.Property(User::class, User::age),
                    alias = "avg_age"
                )
            )
        )

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT COUNT(*) AS total, AVG(user.age) AS avg_age FROM user", rendered.sql)
    }

    @Test
    fun `test SELECT with ORDER BY`() {
        val query = SelectQuery(
            from = User::class,
            orderBy = listOf(
                PropertyOrder(User::class, User::name, OrderDirection.ASC),
                PropertyOrder(User::class, User::age, OrderDirection.DESC)
            )
        )

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user ORDER BY user.name ASC, user.age DESC", rendered.sql)
    }

    @Test
    fun `test SELECT with LIMIT and OFFSET`() {
        val query = SelectQuery(
            from = User::class,
            limit = 10,
            offset = 20
        )

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user LIMIT 10 OFFSET 20", rendered.sql)
    }

    @Test
    fun `test SELECT with GROUP BY and HAVING`() {
        val query = SelectQuery(
            from = User::class,
            fields = listOf(
                SelectField.Property(User::class, User::cityId),
                SelectField.Aggregate(
                    function = AggregateFunction.COUNT,
                    field = SelectField.All,
                    alias = "count"
                )
            ),
            groupBy = listOf(
                SelectField.Property(User::class, User::cityId)
            ),
            having = Predicate.Comparison(
                left = Operand.Literal(1), // Simplificado para teste
                operator = ComparisonOperator.GT,
                right = Operand.Literal(5)
            )
        )

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertTrue(rendered.sql.contains("GROUP BY"))
        assertTrue(rendered.sql.contains("HAVING"))
    }

    @Test
    fun `test INSERT query`() {
        val query = InsertQuery(
            into = User::class,
            values = mapOf(
                "name" to "John Doe",
                "email" to "john@example.com",
                "age" to 30
            )
        )

        val renderer = InsertRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("INSERT INTO user (name, email, age) VALUES (?, ?, ?)", rendered.sql)
        assertEquals(listOf("John Doe", "john@example.com", 30), rendered.parameters)
    }

    @Test
    fun `test UPDATE query`() {
        val query = UpdateQuery(
            table = User::class,
            updates = mapOf(
                "name" to "Jane Doe",
                "age" to 25
            ),
            where = Predicate.Comparison(
                left = Operand.Property(User::class, User::id),
                operator = ComparisonOperator.EQ,
                right = Operand.Literal(1L)
            )
        )

        val renderer = UpdateRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertTrue(rendered.sql.startsWith("UPDATE user SET"))
        assertTrue(rendered.sql.contains("WHERE"))
        assertEquals(3, rendered.parameters.size) // 2 updates + 1 where
    }

    @Test
    fun `test DELETE query`() {
        val query = DeleteQuery(
            from = User::class,
            where = Predicate.Comparison(
                left = Operand.Property(User::class, User::age),
                operator = ComparisonOperator.LT,
                right = Operand.Literal(18)
            )
        )

        val renderer = DeleteRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("DELETE FROM user WHERE user.age < ?", rendered.sql)
        assertEquals(listOf(18), rendered.parameters)
    }

    @Test
    fun `test snake_case conversion`() {
        assertEquals("user", EntityRegistry.resolveTable(User::class))
        assertEquals("city_id", EntityRegistry.resolveColumn(User::cityId))
    }

    @Test
    fun `test BETWEEN predicate`() {
        val predicate = Predicate.Between(
            operand = Operand.Property(User::class, User::age),
            lower = 18,
            upper = 65
        )

        val query = SelectQuery(from = User::class, where = predicate)

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.age BETWEEN ? AND ?", rendered.sql)
        assertEquals(listOf(18, 65), rendered.parameters)
    }

    @Test
    fun `test IS NULL predicate`() {
        val predicate = Predicate.IsNull(
            operand = Operand.Property(User::class, User::cityId)
        )

        val query = SelectQuery(from = User::class, where = predicate)

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.city_id IS NULL", rendered.sql)
        assertTrue(rendered.parameters.isEmpty())
    }

    @Test
    fun `test NOT predicate`() {
        val predicate = Predicate.Not(
            Predicate.Comparison(
                left = Operand.Property(User::class, User::age),
                operator = ComparisonOperator.LT,
                right = Operand.Literal(18)
            )
        )

        val query = SelectQuery(from = User::class, where = predicate)

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE NOT (user.age < ?)", rendered.sql)
        assertEquals(listOf(18), rendered.parameters)
    }
}
