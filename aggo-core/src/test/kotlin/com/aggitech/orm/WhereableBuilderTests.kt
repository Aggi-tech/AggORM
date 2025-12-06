package com.aggitech.orm

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.dsl.*
import com.aggitech.orm.enums.PostgresDialect
import com.aggitech.orm.query.model.predicate.Predicate
import com.aggitech.orm.sql.renderer.SelectRenderer
import com.aggitech.orm.sql.renderer.UpdateRenderer
import com.aggitech.orm.sql.renderer.DeleteRenderer
import kotlin.test.*

/**
 * Testes unitários para WhereableBuilder e operadores refatorados
 */
class WhereableBuilderTests {

    @BeforeTest
    fun setup() {
        EntityRegistry.clearCache()
    }

    // ==================== Testes de Operadores de Comparação ====================

    @Test
    fun `eq operator should create EQ predicate`() {
        val query = select<User> {
            where { User::name eq "John" }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.name = ?", rendered.sql)
        assertEquals(listOf("John"), rendered.parameters)
    }

    @Test
    fun `ne operator should create NE predicate`() {
        val query = select<User> {
            where { User::name ne "Admin" }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.name != ?", rendered.sql)
    }

    @Test
    fun `gt operator should create GT predicate`() {
        val query = select<User> {
            where { User::age gt 21 }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.age > ?", rendered.sql)
    }

    @Test
    fun `gte operator should create GTE predicate`() {
        val query = select<User> {
            where { User::age gte 18 }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.age >= ?", rendered.sql)
    }

    @Test
    fun `lt operator should create LT predicate`() {
        val query = select<User> {
            where { User::age lt 65 }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.age < ?", rendered.sql)
    }

    @Test
    fun `lte operator should create LTE predicate`() {
        val query = select<User> {
            where { User::age lte 65 }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.age <= ?", rendered.sql)
    }

    // ==================== Testes de WhereableBuilder em diferentes builders ====================

    @Test
    fun `SelectQueryBuilder should inherit where from WhereableBuilder`() {
        val query = select<User> {
            where { User::name eq "Test" }
        }

        assertNotNull(query.where)
        assertTrue(query.where is Predicate.Comparison)
    }

    @Test
    fun `UpdateQueryBuilder should inherit where from WhereableBuilder`() {
        val query = update<User> {
            User::name to "Updated Name"
            where { User::id eq 1L }
        }

        val renderer = UpdateRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertTrue(rendered.sql.contains("WHERE"))
        assertTrue(rendered.sql.contains("user.id = ?"))
    }

    @Test
    fun `DeleteQueryBuilder should inherit where from WhereableBuilder`() {
        val query = delete<User> {
            where { User::age lt 18 }
        }

        val renderer = DeleteRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("DELETE FROM user WHERE user.age < ?", rendered.sql)
    }

    // ==================== Testes de Operadores Combinados ====================

    @Test
    fun `and operator should combine predicates`() {
        val query = select<User> {
            where { (User::age gte 18) and (User::age lte 65) }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE (user.age >= ? AND user.age <= ?)", rendered.sql)
        assertEquals(listOf(18, 65), rendered.parameters)
    }

    @Test
    fun `or operator should combine predicates`() {
        val query = select<User> {
            where { (User::name eq "John") or (User::name eq "Jane") }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE (user.name = ? OR user.name = ?)", rendered.sql)
    }

    @Test
    fun `not operator should negate predicate`() {
        val query = select<User> {
            where { not(User::age lt 18) }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE NOT (user.age < ?)", rendered.sql)
    }

    // ==================== Testes de Outros Operadores ====================

    @Test
    fun `like operator should create LIKE predicate`() {
        val query = select<User> {
            where { User::email like "%@gmail.com" }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.email LIKE ?", rendered.sql)
    }

    @Test
    fun `inList operator should create IN predicate`() {
        val query = select<User> {
            where { User::cityId inList listOf(1L, 2L, 3L) }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.city_id IN (?, ?, ?)", rendered.sql)
    }

    @Test
    fun `isNull should create IS NULL predicate`() {
        val query = select<User> {
            where { User::cityId.isNull() }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.city_id IS NULL", rendered.sql)
    }

    @Test
    fun `between should create BETWEEN predicate`() {
        val query = select<User> {
            where { User::age.between(18, 65) }
        }

        val renderer = SelectRenderer(PostgresDialect)
        val rendered = renderer.render(query)

        assertEquals("SELECT * FROM user WHERE user.age BETWEEN ? AND ?", rendered.sql)
    }
}
