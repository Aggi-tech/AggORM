package com.aggitech.orm.examples

import com.aggitech.orm.entities.annotations.*

/**
 * Shared entities for all examples to avoid redeclaration errors
 */

@Table(name = "users")
data class User(
    @PrimaryKey
    val id: Long? = null,

    @NotNull(message = "Name is required")
    @Size(min = 2, max = 100)
    val name: String,

    @NotNull
    @Email
    val email: String,

    @Min(18)
    @Max(120)
    val age: Int,

    @ForeignKey(references = City::class)
    val cityId: Long? = null
)

@Table(name = "cities")
data class City(
    @PrimaryKey
    val id: Long? = null,

    @NotNull
    @Size(min = 2, max = 100)
    val name: String,

    @NotNull
    @Size(min = 2, max = 2)
    val state: String,

    @NotNull
    @Size(min = 2, max = 100)
    val country: String = "USA"
)

@Table(name = "orders")
data class Order(
    @PrimaryKey
    val id: Long? = null,

    @ForeignKey(references = User::class, onDelete = CascadeType.CASCADE)
    val userId: Long,

    @NotNull
    val product: String,

    @NotNull
    @Min(0)
    val amount: Double,

    @NotNull
    @Min(0)
    val totalAmount: Double = 0.0,

    val status: String? = "PENDING",

    val createdAt: String? = null
)
