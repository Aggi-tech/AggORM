# Entity Validation - Validação de Entidades

O AggORM fornece um sistema de validação baseado em annotations para garantir a integridade dos dados antes de persistir no banco.

## EntityValidator

Classe responsável por validar entidades com base em annotations de validação.

### Características

- Validação baseada em annotations
- Suporte a múltiplas regras por propriedade
- Mensagens de erro descritivas
- Integração com reflection do Kotlin
- Validação de tipos primitivos e Strings

## Annotations Suportadas

### @NotNull

Valida que um valor não é nulo:

```kotlin
import jakarta.validation.constraints.NotNull

data class User(
    @field:NotNull
    val name: String?,

    @field:NotNull
    val email: String?
)

// [OK] Válido
val validUser = User(name = "John", email = "john@example.com")
validUser.validate()

// [AVOID] Inválido
val invalidUser = User(name = null, email = "john@example.com")
invalidUser.validate()
// ValidationResult(valid=false, errors=["name cannot be null"])
```

### @Size

Valida o tamanho de Strings e coleções:

```kotlin
import jakarta.validation.constraints.Size

data class User(
    @field:Size(min = 3, max = 50)
    val username: String,

    @field:Size(min = 8, max = 100)
    val password: String,

    @field:Size(max = 200)
    val bio: String?
)

// [OK] Válido
val validUser = User(
    username = "john_doe",
    password = "securePassword123",
    bio = "Software developer"
)

// [AVOID] Inválido - username muito curto
val invalidUser = User(
    username = "ab",
    password = "securePassword123",
    bio = null
)
invalidUser.validate()
// ValidationResult(valid=false, errors=["username size must be between 3 and 50"])
```

### @Min e @Max

Valida valores numéricos mínimos e máximos:

```kotlin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Max

data class Product(
    @field:Min(0)
    val price: Double,

    @field:Min(0)
    @field:Max(1000)
    val stock: Int,

    @field:Max(100)
    val discountPercent: Int
)

// [OK] Válido
val validProduct = Product(
    price = 99.99,
    stock = 50,
    discountPercent = 10
)

// [AVOID] Inválido - preço negativo
val invalidProduct = Product(
    price = -10.0,
    stock = 50,
    discountPercent = 10
)
invalidProduct.validate()
// ValidationResult(valid=false, errors=["price must be at least 0"])
```

### @Range

Valida que um valor está dentro de um intervalo:

```kotlin
import com.aggitech.orm.validation.Range

data class User(
    @field:Range(min = 18, max = 120)
    val age: Int,

    @field:Range(min = 0, max = 5)
    val rating: Double
)

// [OK] Válido
val validUser = User(age = 25, rating = 4.5)

// [AVOID] Inválido - idade fora do range
val invalidUser = User(age = 150, rating = 4.5)
invalidUser.validate()
// ValidationResult(valid=false, errors=["age must be between 18 and 120"])
```

### @Email

Valida formato de email:

```kotlin
import jakarta.validation.constraints.Email

data class User(
    @field:Email
    val email: String,

    @field:Email
    val alternativeEmail: String?
)

// [OK] Válido
val validUser = User(
    email = "john@example.com",
    alternativeEmail = "john.doe@company.org"
)

// [AVOID] Inválido - formato de email inválido
val invalidUser = User(
    email = "not-an-email",
    alternativeEmail = null
)
invalidUser.validate()
// ValidationResult(valid=false, errors=["email must be a valid email address"])
```

### @Pattern

Valida usando expressão regular:

```kotlin
import jakarta.validation.constraints.Pattern

data class User(
    @field:Pattern(regexp = "^[a-zA-Z0-9_]{3,20}$")
    val username: String,

    @field:Pattern(regexp = "^\\+?[1-9]\\d{1,14}$")
    val phoneNumber: String?
)

// [OK] Válido
val validUser = User(
    username = "john_doe_123",
    phoneNumber = "+5511999999999"
)

// [AVOID] Inválido - username com caracteres especiais
val invalidUser = User(
    username = "john@doe!",
    phoneNumber = "+5511999999999"
)
invalidUser.validate()
// ValidationResult(valid=false, errors=["username must match pattern ^[a-zA-Z0-9_]{3,20}$"])
```

## Uso

### Método validate()

Retorna um `ValidationResult` com detalhes:

```kotlin
data class User(
    @field:NotNull
    @field:Size(min = 3, max = 50)
    val name: String?,

    @field:Email
    val email: String,

    @field:Range(min = 18, max = 120)
    val age: Int
)

val user = User(name = "Jo", email = "invalid", age = 15)

val result = user.validate()

if (result.valid) {
    println("Usuário válido!")
} else {
    println("Erros de validação:")
    result.errors.forEach { println("  - $it") }
}

// Output:
// Erros de validação:
//   - name size must be between 3 and 50
//   - email must be a valid email address
//   - age must be between 18 and 120
```

### Método validateAndThrow()

Lança exceção se inválido:

```kotlin
val user = User(name = "Jo", email = "invalid", age = 15)

try {
    user.validateAndThrow()
    // Continua apenas se válido
    insert(user).execute()
} catch (e: ValidationException) {
    println("Validação falhou: ${e.errors}")
}
```

## ValidationResult

Classe que representa o resultado da validação:

```kotlin
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>
)

// Exemplo de uso
val result = entity.validate()

if (!result.valid) {
    logger.error("Validation failed: ${result.errors.joinToString(", ")}")
    throw ValidationException(result.errors)
}
```

## ValidationException

Exceção lançada por `validateAndThrow()`:

```kotlin
class ValidationException(
    val errors: List<String>
) : RuntimeException("Validation failed: ${errors.joinToString(", ")}")

// Capturar e processar
try {
    user.validateAndThrow()
} catch (e: ValidationException) {
    e.errors.forEach { error ->
        // Processar cada erro individualmente
        println("Erro: $error")
    }
}
```

## Múltiplas Validações

Você pode combinar múltiplas annotations:

```kotlin
data class User(
    @field:NotNull
    @field:Size(min = 3, max = 50)
    @field:Pattern(regexp = "^[a-zA-Z0-9_]+$")
    val username: String?,

    @field:NotNull
    @field:Email
    @field:Size(max = 100)
    val email: String?,

    @field:Min(0)
    @field:Max(150)
    val age: Int
)

// Todas as validações são verificadas
val user = User(
    username = "ab",           // Falha em @Size (min=3)
    email = "not-an-email",    // Falha em @Email
    age = 200                  // Falha em @Max(150)
)

val result = user.validate()
// ValidationResult(
//   valid = false,
//   errors = [
//     "username size must be between 3 and 50",
//     "email must be a valid email address",
//     "age must be at most 150"
//   ]
// )
```

## Integração com Inserção

```kotlin
fun createUser(name: String?, email: String, age: Int) {
    val user = User(name = name, email = email, age = age)

    // Validar antes de inserir
    user.validateAndThrow()

    // Se chegou aqui, é válido
    insert(user).execute()
}

// Uso
try {
    createUser(name = "John", email = "john@example.com", age = 25)
    println("Usuário criado com sucesso!")
} catch (e: ValidationException) {
    println("Falha na validação: ${e.errors}")
}
```

## Validação Customizada

Para validações mais complexas, você pode criar funções de validação customizadas:

```kotlin
data class User(
    @field:NotNull
    val username: String?,

    @field:NotNull
    val password: String?,

    @field:NotNull
    val confirmPassword: String?
) {
    fun validatePasswordMatch(): ValidationResult {
        val errors = mutableListOf<String>()

        // Validações padrão
        val standardValidation = this.validate()
        errors.addAll(standardValidation.errors)

        // Validação customizada
        if (password != confirmPassword) {
            errors.add("password and confirmPassword must match")
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors
        )
    }
}

// Uso
val user = User(
    username = "john",
    password = "password123",
    confirmPassword = "password456"
)

val result = user.validatePasswordMatch()
// ValidationResult(valid=false, errors=["password and confirmPassword must match"])
```

## Boas Práticas

### [OK] Fazer

```kotlin
// Sempre validar antes de persistir
fun createUser(user: User) {
    user.validateAndThrow()
    insert(user).execute()
}

// Combinar validações apropriadamente
data class User(
    @field:NotNull
    @field:Email
    val email: String?,

    @field:NotNull
    @field:Size(min = 8)
    val password: String?
)

// Fornecer feedback claro ao usuário
try {
    user.validateAndThrow()
} catch (e: ValidationException) {
    return Response.badRequest(e.errors)
}
```

### [AVOID] Evitar

```kotlin
// Não ignorar validação
insert(user).execute()  // Pode inserir dados inválidos!

// Não usar validação em propriedades nullable sem @NotNull
data class User(
    @field:Size(min = 3)  // Sem @NotNull
    val name: String?     // Pode ser null e passar validação
)

// Não validar muito tarde
fun processUser(user: User) {
    // ... muito processamento ...
    user.validateAndThrow()  // Deveria validar no início
}
```

## Exemplo Completo

```kotlin
data class CreateUserRequest(
    @field:NotNull
    @field:Size(min = 3, max = 50)
    @field:Pattern(regexp = "^[a-zA-Z0-9_]+$")
    val username: String?,

    @field:NotNull
    @field:Email
    val email: String?,

    @field:NotNull
    @field:Size(min = 8, max = 100)
    val password: String?,

    @field:Range(min = 18, max = 120)
    val age: Int,

    @field:Size(max = 500)
    val bio: String? = null
)

fun createUser(request: CreateUserRequest): Result<User> {
    return try {
        // Validar request
        request.validateAndThrow()

        // Criar usuário
        val user = User(
            username = request.username!!,
            email = request.email!!,
            passwordHash = hashPassword(request.password!!),
            age = request.age,
            bio = request.bio
        )

        // Inserir no banco
        insert(user).execute()

        Result.success(user)
    } catch (e: ValidationException) {
        Result.failure(e)
    }
}
```
