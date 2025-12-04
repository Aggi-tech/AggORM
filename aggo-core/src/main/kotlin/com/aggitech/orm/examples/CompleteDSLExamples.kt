package com.aggitech.orm.examples

import com.aggitech.orm.core.metadata.EntityRegistry
import com.aggitech.orm.dsl.*
import com.aggitech.orm.enums.PostgresDialect
import com.aggitech.orm.sql.renderer.*
import com.aggitech.orm.validation.validate

// Entities are now in ExampleEntities.kt to avoid redeclaration errors

fun exampleSimpleSelect() {
    val query = select<User> {
        select {
            +User::name
            +User::email
            +User::age
        }
        where {
            User::age gte 18
        }
        orderBy {
            User::name.asc()
        }
        limit(10)
    }

    val renderer = com.aggitech.orm.sql.renderer.SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}")
    println("Parameters: ${rendered.parameters}\n")
}

fun exampleComplexWhere() {
    val query = select<User> {
        where {
            ((User::age gte 18) and (User::age lte 65)) and
                    (User::email like "%@gmail.com")
        }
        orderBy {
            User::age.desc()
        }
    }

    val renderer = com.aggitech.orm.sql.renderer.SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}")
    println("Parameters: ${rendered.parameters}\n")
}

fun exampleAggregates() {
    val query = select<User> {
        select {
            countAll("total_users")
            avg(User::age, "average_age")
            min(User::age, "min_age")
            max(User::age, "max_age")
        }
    }

    val renderer = com.aggitech.orm.sql.renderer.SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}\n")
}

fun exampleGroupByHaving() {
    val query = select<Order> {
        select {
            +Order::userId
            sum(Order::totalAmount, "total_spent")
            countAll("order_count")
        }
        groupBy(Order::userId)
        having {
            Order::totalAmount gt 1000.0
        }
        orderBy {
            Order::totalAmount.desc()
        }
    }

    val renderer = com.aggitech.orm.sql.renderer.SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}")
    println("Parameters: ${rendered.parameters}\n")
}

fun exampleInAndBetween() {
    val query = select<User> {
        where {
            (User::cityId inList listOf(1L, 2L, 3L, 5L, 8L)) and
                    (User::age.between(25, 45))
        }
    }

    val renderer = com.aggitech.orm.sql.renderer.SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}")
    println("Parameters: ${rendered.parameters}\n")
}

fun exampleNullChecks() {
    val query = select<User> {
        where {
            User::cityId.isNull()
        }
    }

    val renderer = com.aggitech.orm.sql.renderer.SelectRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}\n")
}

fun exampleInsert() {
    val query = insert<User> {
        User::name to "John Doe"
        User::email to "john@example.com"
        User::age to 30
        User::cityId to 1L
    }

    val renderer = com.aggitech.orm.sql.renderer.InsertRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}")
    println("Parameters: ${rendered.parameters}\n")
}

fun exampleInsertEntity() {
    val user = User(
        name = "Jane Smith",
        email = "jane@example.com",
        age = 28,
        cityId = 2L
    )

    val query = insert(user)

    val renderer = com.aggitech.orm.sql.renderer.InsertRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}")
    println("Parameters: ${rendered.parameters}\n")
}

fun exampleUpdate() {
    val query = update<User> {
        User::name to "John Updated"
        User::age to 31
        where {
            User::id eq 1L
        }
    }

    val renderer = com.aggitech.orm.sql.renderer.UpdateRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}")
    println("Parameters: ${rendered.parameters}\n")
}

fun exampleConditionalUpdate() {
    val query = update<User> {
        User::cityId to null
        where {
            (User::age lt 18) or (User::age gt 100)
        }
    }

    val renderer = com.aggitech.orm.sql.renderer.UpdateRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}")
    println("Parameters: ${rendered.parameters}\n")
}

fun exampleDelete() {
    val query = delete<User> {
        where {
            User::id eq 999L
        }
    }

    val renderer = com.aggitech.orm.sql.renderer.DeleteRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}")
    println("Parameters: ${rendered.parameters}\n")
}

fun exampleConditionalDelete() {
    val query = delete<User> {
        where {
            (User::age lt 18) and (User::email like "%@temporary.com")
        }
    }

    val renderer = com.aggitech.orm.sql.renderer.DeleteRenderer(PostgresDialect)
    val rendered = renderer.render(query)

    println("SQL: ${rendered.sql}")
    println("Parameters: ${rendered.parameters}\n")
}

fun exampleValidation() {
    val validUser = User(
        id = 1L,
        name = "Alice Johnson",
        email = "alice@example.com",
        age = 25,
        cityId = 1L
    )

    val validResult = validUser.validate()
    println("Valid user: ${validResult.isValid}")

    val invalidUser = User(
        id = 2L,
        name = "B",
        email = "invalid-email",
        age = 15,
        cityId = 1L
    )

    val invalidResult = invalidUser.validate()
    println("Invalid user: ${invalidResult.isValid}")
    invalidResult.errors.forEach { error ->
        println("  ${error.property}: ${error.message}")
    }
    println()
}

fun exampleSnakeCase() {
    println("User -> ${EntityRegistry.resolveTable(User::class)}")
    println("City -> ${EntityRegistry.resolveTable(City::class)}")
    println("User::cityId -> ${EntityRegistry.resolveColumn(User::cityId)}")
    println("User::name -> ${EntityRegistry.resolveColumn(User::name)}\n")
}

fun main() {
    exampleSimpleSelect()
    exampleComplexWhere()
    exampleAggregates()
    exampleGroupByHaving()
    exampleInAndBetween()
    exampleNullChecks()
    exampleInsert()
    exampleInsertEntity()
    exampleUpdate()
    exampleConditionalUpdate()
    exampleDelete()
    exampleConditionalDelete()
    exampleValidation()
    exampleSnakeCase()
}
