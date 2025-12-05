package com.aggitech.orm.examples

import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.dsl.*
import com.aggitech.orm.enums.SupportedDatabases
import com.aggitech.orm.jdbc.*
import com.aggitech.orm.logging.LogLevel
import com.aggitech.orm.logging.QueryLoggerManager

/**
 * Exemplo completo de uso do AggORM com JDBC
 *
 * Demonstra todas as melhorias implementadas:
 * - Connection Manager com pool automático
 * - Execução sem passar Connection/Dialect
 * - Mapeamento automático para entidades
 * - Logging de queries
 * - Transações
 *
 * Modelos de exemplo: veja ExampleModels.kt
 */

// ==================== Exemplo Completo ====================

fun main() {
    println("""
        ╔══════════════════════════════════════════════════════════╗
        ║                                                          ║
        ║           AggORM - JDBC Example                          ║
        ║                                                          ║
        ║  Demonstração das melhorias implementadas:               ║
        ║  ✓ Connection Manager com pool automático                ║
        ║  ✓ Execução ergonômica (sem Connection/Dialect)          ║
        ║  ✓ Mapeamento automático para entidades                  ║
        ║  ✓ Logging de queries                                    ║
        ║  ✓ Transações simplificadas                              ║
        ║                                                          ║
        ╚══════════════════════════════════════════════════════════╝
    """.trimIndent())

    // ==================== 1. Configuração Inicial ====================
    println("\n[1] Configuração Inicial\n")

    val config = DbConfig(
        database = "myapp",
        host = "localhost",
        port = 5432,
        user = "postgres",
        password = "password",
        type = SupportedDatabases.POSTGRESQL
    )

    // Registra a configuração uma vez no início da aplicação
    JdbcConnectionManager.register(config = config)
    println("✓ Connection Manager configurado")

    // Habilita logging de queries
    QueryLoggerManager.enableConsoleLogging(
        logLevel = LogLevel.DEBUG,
        includeParameters = true,
        includeExecutionTime = true
    )
    println("✓ Logging habilitado\n")

    // ==================== 2. SELECT Queries ====================
    println("[2] SELECT Queries\n")

    // SELECT básico retornando Map<String, Any?>
    println("→ SELECT retornando Map:")
    val usersMap: List<Map<String, Any?>> = select<User> {
        where {
            User::age gte 18
        }
        orderBy {
            User::name.asc()
        }
        limit(10)
    }.execute()

    usersMap.take(2).forEach { user ->
        println("  - ${user["name"]} (${user["age"]} anos)")
    }

    // SELECT retornando entidades tipadas (NOVO!)
    println("\n→ SELECT retornando entidades tipadas:")
    val users: List<User> = select<User> {
        where {
            User::age gte 18
        }
        orderBy {
            User::name.asc()
        }
    }.executeAsEntities()

    users.take(2).forEach { user ->
        println("  - ${user.name} (${user.age} anos) - ${user.email}")
    }

    // SELECT single result (NOVO!)
    println("\n→ SELECT single result:")
    val user: User? = select<User> {
        where {
            User::id eq 1L
        }
    }.executeAsEntityOrNull()

    println("  - User ID 1: ${user?.name ?: "não encontrado"}")

    // SELECT com agregações
    println("\n→ SELECT com agregações:")
    val stats = select<User> {
        select {
            countAll("total")
            avg(User::age, "average_age")
            min(User::age, "min_age")
            max(User::age, "max_age")
        }
    }.execute()

    stats.firstOrNull()?.let { stat ->
        println("  Total: ${stat["total"]}")
        println("  Idade média: ${stat["average_age"]}")
        println("  Idade mínima: ${stat["min_age"]}")
        println("  Idade máxima: ${stat["max_age"]}")
    }

    // ==================== 3. INSERT Operations ====================
    println("\n[3] INSERT Operations\n")

    // INSERT básico
    println("→ INSERT básico:")
    val insertedRows = insert<User> {
        User::name to "João Silva"
        User::email to "joao@example.com"
        User::age to 30
    }.execute()
    println("  ✓ Inserido $insertedRows linha(s)")

    // INSERT com retorno de ID (NOVO!)
    println("\n→ INSERT com retorno de ID:")
    val generatedIds = insert<User> {
        User::name to "Maria Santos"
        User::email to "maria@example.com"
        User::age to 25
    }.executeReturningKeys()
    println("  ✓ ID gerado: ${generatedIds.firstOrNull()}")

    // ==================== 4. UPDATE Operations ====================
    println("\n[4] UPDATE Operations\n")

    val updatedRows = update<User> {
        User::age to 31
        where {
            User::email eq "joao@example.com"
        }
    }.execute()
    println("✓ Atualizado $updatedRows linha(s)")

    // ==================== 5. DELETE Operations ====================
    println("\n[5] DELETE Operations\n")

    val deletedRows = delete<User> {
        where {
            User::age lt 18
        }
    }.execute()
    println("✓ Deletado $deletedRows linha(s)")

    // ==================== 6. Transações (NOVO!) ====================
    println("\n[6] Transações\n")

    try {
        transaction {
            println("→ Iniciando transação...")

            // INSERT de user
            val userId = insert<User> {
                User::name to "Carlos Oliveira"
                User::email to "carlos@example.com"
                User::age to 35
            }.executeReturningKeys().first()

            println("  ✓ User criado: ID $userId")

            // INSERT de order
            val orderId = insert<Order> {
                Order::userId to userId
                Order::totalAmount to 150.00
                Order::status to "PENDING"
            }.executeReturningKeys().first()

            println("  ✓ Order criado: ID $orderId")

            // Commit automático ao sair do bloco
        }
        println("✓ Transação completada com sucesso!")
    } catch (e: Exception) {
        println("✗ Transação falhou: ${e.message}")
        // Rollback automático em caso de exceção
    }

    // ==================== 7. Queries Complexas ====================
    println("\n[7] Queries Complexas\n")

    println("→ GROUP BY com HAVING:")
    val orderStats = select<Order> {
        select {
            +Order::userId
            countAll("order_count")
            sum(Order::totalAmount, "total_spent")
        }
        groupBy(Order::userId)
        having {
            Order::totalAmount gt 100.0
        }
        orderBy {
            Order::totalAmount.desc()
        }
    }.execute()

    orderStats.take(3).forEach { stat ->
        println("  User ${stat["userId"]}: ${stat["order_count"]} pedidos, total R$ ${stat["total_spent"]}")
    }

    // ==================== 8. Performance ====================
    println("\n[8] Performance\n")

    val start = System.currentTimeMillis()
    val allUsers = select<User> {
        limit(100)
    }.executeAsEntities()
    val time = System.currentTimeMillis() - start

    println("✓ ${allUsers.size} usuários carregados em ${time}ms")

    // ==================== Limpeza ====================
    println("\n[Finalizando]\n")
    JdbcConnectionManager.close()
    println("✓ Connection pool fechado")
    println("\n" + "=".repeat(60))
}
