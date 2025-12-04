package com.aggitech.orm

import com.aggitech.orm.dsl.*
import com.aggitech.orm.enums.PostgresDialect
import com.aggitech.orm.sql.renderer.SelectRenderer
import kotlin.test.*

class DSLTests {

    @Test
    fun `test SELECT DSL with where`() {
        val query = select<User> {
            select {
                +User::name
                +User::email
            }
            where {
                (User::age gte 18) and (User::email like "%@gmail.com")
            }
            limit(10)
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertTrue(rendered.sql.contains("WHERE"))
        assertTrue(rendered.sql.contains("LIMIT 10"))
    }

    @Test
    fun `test SELECT DSL with aggregates`() {
        val query = select<User> {
            select {
                countAll("total")
                avg(User::age, "avg_age")
            }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertTrue(rendered.sql.contains("COUNT(*)"))
        assertTrue(rendered.sql.contains("AVG(user.age)"))
    }

    @Test
    fun `test SELECT DSL with ORDER BY`() {
        val query = select<User> {
            select {
                +User::name
                +User::age
            }
            orderBy {
                User::name.asc()
                User::age.desc()
            }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertTrue(rendered.sql.contains("ORDER BY"))
        assertTrue(rendered.sql.contains("ASC"))
        assertTrue(rendered.sql.contains("DESC"))
    }

    @Test
    fun `test INSERT DSL`() {
        val query = insert<User> {
            User::name to "John Doe"
            User::email to "john@example.com"
            User::age to 30
        }

        assertEquals(User::class, query.into)
        assertEquals(3, query.values.size)
    }

    @Test
    fun `test UPDATE DSL`() {
        val query = update<User> {
            User::name to "Jane Doe"
            User::age to 25
            where {
                User::id eq 1L
            }
        }

        assertEquals(User::class, query.table)
        assertEquals(2, query.updates.size)
        assertNotNull(query.where)
    }

    @Test
    fun `test DELETE DSL`() {
        val query = delete<User> {
            where {
                User::age lt 18
            }
        }

        assertEquals(User::class, query.from)
        assertNotNull(query.where)
    }

    @Test
    fun `test WHERE DSL operators`() {
        val query = select<User> {
            where {
                User::age.between(18, 65)
            }
        }

        assertNotNull(query.where)
    }

    @Test
    fun `test WHERE DSL IN operator`() {
        val query = select<User> {
            where {
                User::cityId inList listOf(1L, 2L, 3L)
            }
        }

        assertNotNull(query.where)
    }

    @Test
    fun `test WHERE DSL IS NULL`() {
        val query = select<User> {
            where {
                User::cityId.isNull()
            }
        }

        assertNotNull(query.where)
    }

    @Test
    fun `test complex WHERE with AND and OR`() {
        val query = select<User> {
            where {
                ((User::age gte 18) and (User::age lte 65)) or (User::name like "Admin%")
            }
        }

        assertNotNull(query.where)
    }
}
