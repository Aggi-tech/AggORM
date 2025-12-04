package com.aggitech.orm

import com.aggitech.orm.entities.annotations.*
import com.aggitech.orm.validation.EntityValidator
import com.aggitech.orm.validation.ValidationException
import com.aggitech.orm.validation.validate
import com.aggitech.orm.validation.validateAndThrow
import kotlin.test.*

// Entidade de teste com validações
data class ValidatedUser(
    @NotNull
    val id: Long?,

    @NotNull(message = "Name is required")
    @Size(min = 2, max = 100)
    val name: String?,

    @NotNull
    @Email
    val email: String?,

    @Min(18)
    @Max(120)
    val age: Int?,

    @Pattern(regex = "^\\d{3}-\\d{2}-\\d{4}$", message = "Invalid SSN format")
    val ssn: String? = null
)

class ValidationTests {
    private val validator = EntityValidator()

    @Test
    fun `test valid entity passes validation`() {
        val user = ValidatedUser(
            id = 1L,
            name = "John Doe",
            email = "john@example.com",
            age = 30,
            ssn = "123-45-6789"
        )

        val result = validator.validate(user)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `test NotNull validation fails`() {
        val user = ValidatedUser(
            id = null,
            name = "John Doe",
            email = "john@example.com",
            age = 30
        )

        val result = validator.validate(user)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.property == "id" })
    }

    @Test
    fun `test Size validation fails`() {
        val user = ValidatedUser(
            id = 1L,
            name = "J", // Muito curto
            email = "john@example.com",
            age = 30
        )

        val result = validator.validate(user)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.property == "name" })
    }

    @Test
    fun `test Email validation fails`() {
        val user = ValidatedUser(
            id = 1L,
            name = "John Doe",
            email = "invalid-email", // Email inválido
            age = 30
        )

        val result = validator.validate(user)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.property == "email" })
    }

    @Test
    fun `test Min validation fails`() {
        val user = ValidatedUser(
            id = 1L,
            name = "John Doe",
            email = "john@example.com",
            age = 15 // Menor que 18
        )

        val result = validator.validate(user)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.property == "age" })
    }

    @Test
    fun `test Max validation fails`() {
        val user = ValidatedUser(
            id = 1L,
            name = "John Doe",
            email = "john@example.com",
            age = 150 // Maior que 120
        )

        val result = validator.validate(user)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.property == "age" })
    }

    @Test
    fun `test Pattern validation fails`() {
        val user = ValidatedUser(
            id = 1L,
            name = "John Doe",
            email = "john@example.com",
            age = 30,
            ssn = "12345" // Formato inválido
        )

        val result = validator.validate(user)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.property == "ssn" })
    }

    @Test
    fun `test multiple validation errors`() {
        val user = ValidatedUser(
            id = null,
            name = "J",
            email = "invalid",
            age = 15
        )

        val result = validator.validate(user)

        assertFalse(result.isValid)
        assertTrue(result.errors.size >= 4) // Múltiplos erros
    }

    @Test
    fun `test validateAndThrow extension throws exception`() {
        val user = ValidatedUser(
            id = null,
            name = "John Doe",
            email = "john@example.com",
            age = 30
        )

        assertFailsWith<ValidationException> {
            user.validateAndThrow()
        }
    }

    @Test
    fun `test validate extension returns result`() {
        val user = ValidatedUser(
            id = 1L,
            name = "John Doe",
            email = "john@example.com",
            age = 30
        )

        val result = user.validate()

        assertTrue(result.isValid)
    }
}
