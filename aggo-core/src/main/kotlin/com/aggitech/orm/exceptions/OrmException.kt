package com.aggitech.orm.exceptions

import kotlin.reflect.KClass

/**
 * Exceção base para todas as exceções do ORM
 */
open class OrmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exceção lançada quando validação de entidade falha
 */
class ValidationException(
    message: String
) : OrmException(message)

/**
 * Exceção lançada quando há violação de constraint UNIQUE
 */
class UniqueConstraintViolationException(
    val table: String,
    val column: String,
    val value: Any?
) : OrmException("Unique constraint violation on $table.$column with value: $value")

/**
 * Exceção lançada quando há violação de chave estrangeira
 */
class ForeignKeyViolationException(
    val table: String,
    val column: String,
    val referencedTable: String
) : OrmException("Foreign key constraint violation on $table.$column referencing $referencedTable")

/**
 * Exceção lançada quando entidade não é encontrada
 */
class EntityNotFoundException(
    val entityClass: KClass<*>,
    val id: Any
) : OrmException("Entity ${entityClass.simpleName} with id $id not found")

/**
 * Exceção lançada quando query é inválida
 */
class InvalidQueryException(
    message: String
) : OrmException(message)

/**
 * Exceção lançada quando há erro no mapeamento de ResultSet para entidade
 */
class MappingException(
    message: String,
    cause: Throwable? = null
) : OrmException(message, cause)

/**
 * Exceção lançada quando há erro em transação
 */
class TransactionException(
    message: String,
    cause: Throwable? = null
) : OrmException(message, cause)
