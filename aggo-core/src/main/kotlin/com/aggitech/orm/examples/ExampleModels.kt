package com.aggitech.orm.examples

/**
 * Modelos de exemplo compartilhados entre os exemplos JDBC e R2DBC
 */

data class User(
    val id: Long? = null,
    val name: String,
    val email: String,
    val age: Int
)

data class Order(
    val id: Long? = null,
    val userId: Long,
    val totalAmount: Double,
    val status: String = "PENDING"
)
