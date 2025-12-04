package com.aggitech.orm.examples

import com.aggitech.orm.config.R2dbcConfig
import com.aggitech.orm.dsl.*
import com.aggitech.orm.enums.SupportedDatabases
import com.aggitech.orm.logging.LogLevel
import com.aggitech.orm.logging.QueryLoggerManager
import com.aggitech.orm.r2dbc.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking

/**
 * Exemplo completo de uso do AggORM com R2DBC (Reativo)
 *
 * Demonstra todas as melhorias implementadas:
 * - Connection Manager reativo com pool thread-safe
 * - Execução reativa sem passar Connection/Dialect
 * - Mapeamento automático para entidades
 * - Streaming com Flow
 * - Logging de queries
 * - Transações reativas
 *
 * Modelos de exemplo: veja ExampleModels.kt
 */

// ==================== Exemplo Completo ====================

fun main() = runBlocking {
    println("""
        ╔══════════════════════════════════════════════════════════╗
        ║                                                          ║
        ║           AggORM - R2DBC Example (Reactive)              ║
        ║                                                          ║
        ║  Demonstração das melhorias implementadas:               ║
        ║  ✓ Connection Manager reativo com pool thread-safe      ║
        ║  ✓ Execução ergonômica (sem Connection/Dialect)          ║
        ║  ✓ Mapeamento automático para entidades                  ║
        ║  ✓ Streaming com Kotlin Flow                             ║
        ║  ✓ Logging de queries                                    ║
        ║  ✓ Transações reativas                                   ║
        ║                                                          ║
        ╚══════════════════════════════════════════════════════════╝
    """.trimIndent())

    // ==================== 1. Configuração Inicial ====================
    println("\n[1] Configuração Inicial\n")

    val config = R2dbcConfig(
        database = "myapp",
        host = "localhost",
        port = 5432,
        user = "postgres",
        password = "password",
        type = SupportedDatabases.POSTGRESQL
    )

    // Registra a configuração uma vez no início da aplicação
    R2dbcConnectionManager.register(config = config)
    println("✓ R2DBC Connection Manager configurado")

    // Habilita logging de queries
    QueryLoggerManager.enableConsoleLogging(
        logLevel = LogLevel.DEBUG,
        includeParameters = true,
        includeExecutionTime = true
    )
    println("✓ Logging habilitado\n")

    // ==================== 2. SELECT Queries ====================
    println("[2] SELECT Queries (Reactive)\n")

    // SELECT básico retornando Map<String, Any?> (suspend)
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

    // ==================== 3. Streaming com Flow (NOVO!) ====================
    println("\n[3] Streaming com Kotlin Flow\n")

    println("→ Processamento sob demanda com Flow:")
    var count = 0
    select<User> {
        where {
            User::age gte 18
        }
        orderBy {
            User::id.asc()
        }
    }.executeAsEntityFlow()
        .take(5)  // Processa apenas os primeiros 5
        .collect { user: User ->
            count++
            println("  [$count] ${user.name} - ${user.email}")
        }
    println("  ✓ Processados $count usuários em streaming")

    // ==================== 4. INSERT Operations ====================
    println("\n[4] INSERT Operations (Reactive)\n")

    // INSERT básico
    println("→ INSERT básico:")
    val affectedRows = insert<User> {
        User::name to "Ana Costa"
        User::email to "ana@example.com"
        User::age to 28
    }.execute()
    println("  ✓ Inserido $affectedRows linha(s)")

    // INSERT com retorno de ID (NOVO!)
    println("\n→ INSERT com retorno de ID:")
    val generatedIds = insert<User> {
        User::name to "Pedro Lima"
        User::email to "pedro@example.com"
        User::age to 32
    }.executeReturningKeys()
    println("  ✓ ID gerado: ${generatedIds.firstOrNull()}")

    // ==================== 5. UPDATE Operations ====================
    println("\n[5] UPDATE Operations (Reactive)\n")

    val updatedRows = update<User> {
        User::age to 29
        where {
            User::email eq "ana@example.com"
        }
    }.execute()
    println("✓ Atualizado $updatedRows linha(s)")

    // ==================== 6. DELETE Operations ====================
    println("\n[6] DELETE Operations (Reactive)\n")

    val deletedRows = delete<User> {
        where {
            User::age lt 18
        }
    }.execute()
    println("✓ Deletado $deletedRows linha(s)")

    // ==================== 7. Transações Reativas (NOVO!) ====================
    println("\n[7] Transações Reativas\n")

    try {
        transaction {
            println("→ Iniciando transação reativa...")

            // INSERT de user
            val userId = insert<User> {
                User::name to "Fernanda Alves"
                User::email to "fernanda@example.com"
                User::age to 27
            }.executeReturningKeys().first()

            println("  ✓ User criado: ID $userId")

            // INSERT de order
            val orderId = insert<Order> {
                Order::userId to userId
                Order::totalAmount to 200.00
            }.executeReturningKeys().first()

            println("  ✓ Order criado: ID $orderId")

            // Commit automático ao sair do bloco
        }
        println("✓ Transação reativa completada com sucesso!")
    } catch (e: Exception) {
        println("✗ Transação falhou: ${e.message}")
        // Rollback automático em caso de exceção
    }

    // ==================== 8. Queries Complexas ====================
    println("\n[8] Queries Complexas (Reactive)\n")

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

    // ==================== 9. Performance Reativa ====================
    println("\n[9] Performance Reativa\n")

    val start = System.currentTimeMillis()
    val allUsers = select<User> {
        limit(100)
    }.executeAsEntities()
    val time = System.currentTimeMillis() - start

    println("✓ ${allUsers.size} usuários carregados em ${time}ms (reativo)")

    // ==================== 10. Comparação: Map vs Flow ====================
    println("\n[10] Comparação: Coletar todos vs Streaming\n")

    println("→ Coletando todos os resultados:")
    val startAll = System.currentTimeMillis()
    val allResults = select<User> {
        limit(1000)
    }.executeAsEntities()
    val timeAll = System.currentTimeMillis() - startAll
    println("  ✓ ${allResults.size} usuários em ${timeAll}ms (carrega tudo na memória)")

    println("\n→ Processando com streaming:")
    val startFlow = System.currentTimeMillis()
    var flowCount = 0
    select<User> {
        limit(1000)
    }.executeAsEntityFlow()
        .collect { user ->
            flowCount++
            // Processa um por vez sem carregar tudo na memória
        }
    val timeFlow = System.currentTimeMillis() - startFlow
    println("  ✓ $flowCount usuários em ${timeFlow}ms (processa sob demanda)")

    // ==================== 11. Pool Stats ====================
    println("\n[11] Estatísticas do Pool\n")

    val poolStats = R2dbcConnectionManager.getStats()
    println("→ Pool de conexões:")
    println("  Total de conexões: ${poolStats.totalConnections}")
    println("  Conexões ativas: ${poolStats.activeConnections}")
    println("  Conexões disponíveis: ${poolStats.availableConnections}")
    println("  Tamanho máximo: ${poolStats.maxSize}")
    println("  Utilização: ${String.format("%.1f", poolStats.utilizationPercent())}%")

    // ==================== Limpeza ====================
    println("\n[Finalizando]\n")
    R2dbcConnectionManager.close()
    println("✓ R2DBC Connection pool fechado")
    println("\n" + "=".repeat(60))
}
