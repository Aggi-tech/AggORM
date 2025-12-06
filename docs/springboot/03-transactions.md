# Spring Boot - Transactions

O AggORM integra-se com o sistema de transações do Spring, suportando a annotation `@Transactional`.

## AggoTransactionManager

O starter fornece um `AggoTransactionManager` que implementa `PlatformTransactionManager`:

```kotlin
@Bean
fun aggoTransactionManager(dbConfig: DbConfig): AggoTransactionManager {
    return AggoTransactionManager(dbConfig)
}
```

## Uso Básico

### @Transactional

```kotlin
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository
) {

    @Transactional
    fun createUserWithProfile(
        name: String,
        email: String,
        bio: String
    ): User {
        // Criar usuário
        val user = userRepository.save(
            User(name = name, email = email)
        )

        // Criar perfil
        profileRepository.save(
            Profile(userId = user.id, bio = bio)
        )

        // Ambas operações são committed juntas
        // ou rollback em caso de erro
        return user
    }
}
```

### Rollback Automático

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val inventoryService: InventoryService
) {

    @Transactional
    fun placeOrder(order: Order): Order {
        // Salvar pedido
        val savedOrder = orderRepository.save(order)

        // Reservar estoque
        order.items.forEach { item ->
            inventoryService.reserve(item.productId, item.quantity)
        }

        // Se qualquer operação falhar, tudo é revertido
        return savedOrder
    }
}
```

## Níveis de Isolamento

### Configuração

```kotlin
import org.springframework.transaction.annotation.Isolation

@Service
class AccountService(
    private val accountRepository: AccountRepository
) {

    @Transactional(isolation = Isolation.SERIALIZABLE)
    fun transfer(fromId: Long, toId: Long, amount: BigDecimal) {
        val from = accountRepository.findById(fromId).orElseThrow()
        val to = accountRepository.findById(toId).orElseThrow()

        if (from.balance < amount) {
            throw InsufficientFundsException()
        }

        accountRepository.save(from.copy(balance = from.balance - amount))
        accountRepository.save(to.copy(balance = to.balance + amount))
    }
}
```

### Níveis Disponíveis

| Nível | Descrição |
|-------|-----------|
| `DEFAULT` | Usa o default do banco de dados |
| `READ_UNCOMMITTED` | Permite dirty reads |
| `READ_COMMITTED` | Evita dirty reads |
| `REPEATABLE_READ` | Evita non-repeatable reads |
| `SERIALIZABLE` | Máximo isolamento |

## Propagação

### Tipos de Propagação

```kotlin
import org.springframework.transaction.annotation.Propagation

@Service
class AuditService(
    private val auditRepository: AuditRepository
) {

    // Sempre cria nova transação
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logAction(action: String) {
        auditRepository.save(AuditLog(action = action))
    }
}

@Service
class UserService(
    private val userRepository: UserRepository,
    private val auditService: AuditService
) {

    @Transactional
    fun createUser(name: String): User {
        val user = userRepository.save(User(name = name))

        // Log é salvo em transação separada
        // Mesmo se esta transação falhar, o log é mantido
        auditService.logAction("Created user: $name")

        return user
    }
}
```

### Tipos de Propagação Disponíveis

| Tipo | Descrição |
|------|-----------|
| `REQUIRED` | Usa transação existente ou cria nova (default) |
| `REQUIRES_NEW` | Sempre cria nova transação |
| `SUPPORTS` | Usa transação se existir, senão executa sem |
| `MANDATORY` | Requer transação existente |
| `NOT_SUPPORTED` | Executa sem transação |
| `NEVER` | Falha se houver transação |
| `NESTED` | Cria savepoint dentro da transação |

## Rollback

### Rollback por Exceção

```kotlin
@Service
class PaymentService(
    private val paymentRepository: PaymentRepository
) {

    // Rollback em qualquer Exception
    @Transactional(rollbackFor = [Exception::class])
    fun processPayment(payment: Payment): Payment {
        // ...
    }

    // Rollback apenas para exceções específicas
    @Transactional(rollbackFor = [PaymentException::class])
    fun retryPayment(paymentId: Long) {
        // ...
    }

    // Não fazer rollback para exceções específicas
    @Transactional(noRollbackFor = [WarningException::class])
    fun processWithWarning(payment: Payment) {
        // ...
    }
}
```

### Rollback Programático

```kotlin
import org.springframework.transaction.interceptor.TransactionAspectSupport

@Service
class OrderService(
    private val orderRepository: OrderRepository
) {

    @Transactional
    fun processOrder(order: Order): ProcessingResult {
        try {
            val savedOrder = orderRepository.save(order)
            processPayment(savedOrder)
            return ProcessingResult.success(savedOrder)
        } catch (e: PaymentException) {
            // Marcar para rollback
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
            return ProcessingResult.failed(e.message)
        }
    }
}
```

## Read-Only

### Transações Somente Leitura

```kotlin
@Service
class ReportService(
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository
) {

    @Transactional(readOnly = true)
    fun generateUserReport(): UserReport {
        val users = userRepository.findAll()
        val orders = orderRepository.findAll()

        return UserReport(
            totalUsers = users.count(),
            activeUsers = users.filter { it.active }.count(),
            totalOrders = orders.count()
        )
    }
}
```

Benefícios de `readOnly = true`:
- Otimizações de performance
- Previne modificações acidentais
- Pode usar réplicas de leitura

## Timeout

### Configuração de Timeout

```kotlin
@Service
class BatchService(
    private val batchRepository: BatchRepository
) {

    @Transactional(timeout = 300)  // 5 minutos
    fun processBatch(batchId: Long) {
        // Operação demorada
    }
}
```

## Transações Aninhadas

### Usando Savepoints

```kotlin
@Service
class OrderProcessingService(
    private val orderRepository: OrderRepository,
    private val notificationService: NotificationService
) {

    @Transactional
    fun processOrderWithNotification(order: Order): Order {
        // Salvar pedido
        val savedOrder = orderRepository.save(order)

        try {
            // Tentar notificar (transação aninhada)
            notificationService.sendOrderConfirmation(savedOrder)
        } catch (e: NotificationException) {
            // Falha na notificação não afeta o pedido
            logger.warn("Failed to send notification", e)
        }

        return savedOrder
    }
}

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository
) {

    @Transactional(propagation = Propagation.NESTED)
    fun sendOrderConfirmation(order: Order) {
        // Se falhar, apenas este savepoint é revertido
    }
}
```

## Transações em Controllers

### Transação no Controller

```kotlin
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {

    @PostMapping
    @Transactional
    fun createOrder(@RequestBody request: CreateOrderRequest): Order {
        // Toda a requisição é transacional
        return orderService.createOrder(request)
    }
}
```

### Transação no Service (Recomendado)

```kotlin
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {

    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): Order {
        // Transação gerenciada pelo service
        return orderService.createOrder(request)
    }
}

@Service
class OrderService(
    private val orderRepository: OrderRepository
) {

    @Transactional
    fun createOrder(request: CreateOrderRequest): Order {
        // Lógica transacional aqui
    }
}
```

## Transações Manuais

### TransactionTemplate

```kotlin
import org.springframework.transaction.support.TransactionTemplate

@Service
class ManualTransactionService(
    private val transactionTemplate: TransactionTemplate,
    private val userRepository: UserRepository
) {

    fun createUserManually(name: String): User? {
        return transactionTemplate.execute { status ->
            try {
                userRepository.save(User(name = name))
            } catch (e: Exception) {
                status.setRollbackOnly()
                null
            }
        }
    }
}
```

### PlatformTransactionManager

```kotlin
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionDefinition

@Service
class LowLevelTransactionService(
    private val transactionManager: PlatformTransactionManager,
    private val userRepository: UserRepository
) {

    fun createUserWithManualControl(name: String): User {
        val definition = DefaultTransactionDefinition()
        definition.isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE
        definition.timeout = 60

        val status = transactionManager.getTransaction(definition)

        try {
            val user = userRepository.save(User(name = name))
            transactionManager.commit(status)
            return user
        } catch (e: Exception) {
            transactionManager.rollback(status)
            throw e
        }
    }
}
```

## Exemplo Completo

### Sistema de E-commerce

```kotlin
@Service
class CheckoutService(
    private val orderRepository: OrderRepository,
    private val paymentService: PaymentService,
    private val inventoryService: InventoryService,
    private val notificationService: NotificationService
) {

    @Transactional(
        isolation = Isolation.REPEATABLE_READ,
        rollbackFor = [CheckoutException::class]
    )
    fun checkout(cart: Cart, paymentInfo: PaymentInfo): Order {
        // 1. Validar e reservar estoque
        cart.items.forEach { item ->
            if (!inventoryService.reserve(item.productId, item.quantity)) {
                throw OutOfStockException(item.productId)
            }
        }

        // 2. Criar pedido
        val order = orderRepository.save(Order.fromCart(cart))

        // 3. Processar pagamento
        val payment = paymentService.process(order, paymentInfo)

        if (!payment.success) {
            throw PaymentFailedException(payment.error)
        }

        // 4. Confirmar reservas de estoque
        inventoryService.confirm(order.id)

        // 5. Enviar notificação (transação separada)
        try {
            notificationService.sendOrderConfirmation(order)
        } catch (e: Exception) {
            // Log mas não falha o checkout
            logger.warn("Failed to send confirmation", e)
        }

        return order
    }
}

@Service
class InventoryService(
    private val inventoryRepository: InventoryRepository
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun reserve(productId: Long, quantity: Int): Boolean {
        val inventory = inventoryRepository.findByProductId(productId)
            ?: return false

        if (inventory.available < quantity) {
            return false
        }

        inventoryRepository.save(
            inventory.copy(
                available = inventory.available - quantity,
                reserved = inventory.reserved + quantity
            )
        )

        return true
    }
}
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Colocar @Transactional no service
@Service
class UserService {
    @Transactional
    fun createUser() { }
}

// Especificar rollbackFor explicitamente
@Transactional(rollbackFor = [Exception::class])
fun processOrder() { }

// Usar readOnly para queries
@Transactional(readOnly = true)
fun findAll(): List<User> { }
```

### [AVOID] Evitar

```kotlin
// Não usar @Transactional em métodos privados
@Transactional  // [AVOID] Não funciona em private
private fun helper() { }

// Não chamar método transacional do mesmo objeto
@Service
class MyService {
    fun outer() {
        inner()  // [AVOID] Transação não é aplicada
    }

    @Transactional
    fun inner() { }
}

// Não ignorar exceções em transações
@Transactional
fun process() {
    try {
        riskyOperation()
    } catch (e: Exception) {
        // [AVOID] Exceção engolida, transação pode não ser revertida
    }
}
```

## Próximos Passos

- [Migrations](./04-migrations.md) - Migrations automáticas com Spring Boot
