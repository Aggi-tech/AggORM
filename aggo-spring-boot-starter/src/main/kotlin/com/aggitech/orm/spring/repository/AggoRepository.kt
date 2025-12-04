package com.aggitech.orm.spring.repository

import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.config.JdbcConnectionFactory
import com.aggitech.orm.dsl.*
import com.aggitech.orm.enums.SqlDialect
import com.aggitech.orm.execution.QueryExecutor
import com.aggitech.orm.execution.ResultMapper
import com.aggitech.orm.query.model.*
import com.aggitech.orm.query.model.predicate.Predicate
import com.aggitech.orm.spring.jpa.JpaAnnotationAdapter
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.NoRepositoryBean
import java.sql.Connection
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Interface base compatível com Spring Data Repository
 * Permite usar AggORM com a mesma API do Spring Data
 */
@NoRepositoryBean
interface AggoRepository<T : Any, ID : Any> : CrudRepository<T, ID> {
    /**
     * Busca usando DSL do AggORM
     */
    fun findWhere(block: WhereBuilder<T>.() -> Predicate): List<T>

    /**
     * Busca um usando DSL do AggORM
     */
    fun findOneWhere(block: WhereBuilder<T>.() -> Predicate): T?

    /**
     * Conta registros com condição
     */
    fun countWhere(block: WhereBuilder<T>.() -> Predicate): Long

    /**
     * Deleta com condição
     */
    fun deleteWhere(block: WhereBuilder<T>.() -> Predicate): Int
}

/**
 * Implementação base do AggoRepository
 */
abstract class SimpleAggoRepository<T : Any, ID : Any>(
    private val entityClass: KClass<T>,
    private val dbConfig: DbConfig,
    private val dialect: SqlDialect = com.aggitech.orm.enums.PostgresDialect
) : AggoRepository<T, ID> {

    private val connectionFactory = JdbcConnectionFactory(dbConfig)
    private val mapper = ResultMapper()

    protected fun <R> withConnection(block: (Connection) -> R): R {
        return connectionFactory.open().use(block)
    }

    protected fun <R> withExecutor(block: (QueryExecutor) -> R): R {
        return withConnection { connection ->
            block(QueryExecutor(connection, dialect))
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun getIdProperty(): KProperty1<T, *> {
        return JpaAnnotationAdapter.getIdProperty(entityClass)
            ?: throw IllegalStateException("Entity ${entityClass.simpleName} must have an @Id field")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <S : T> save(entity: S): S {
        withExecutor { executor ->
            val idProperty = getIdProperty()
            val idValue = idProperty.get(entity)

            if (idValue == null) {
                // INSERT
                val query = InsertQuery(into = entityClass, entity = entity)
                executor.executeInsert(query)
            } else {
                // UPDATE - converte entidade para map de updates
                val updates = entityClass.members
                    .filterIsInstance<KProperty1<T, *>>()
                    .filter { !JpaAnnotationAdapter.isPrimaryKey(it) }
                    .associate { prop ->
                        prop.name to prop.get(entity)
                    }

                val predicate = WhereBuilder(entityClass).run {
                    idProperty eq idValue
                }

                val query = UpdateQuery(
                    table = entityClass,
                    updates = updates,
                    where = predicate
                )
                executor.executeUpdate(query)
            }
        }
        return entity
    }

    override fun <S : T> saveAll(entities: Iterable<S>): Iterable<S> {
        entities.forEach { save(it) }
        return entities
    }

    override fun findById(id: ID): java.util.Optional<T> {
        val result = withExecutor { executor ->
            val idProperty = getIdProperty()
            val builder = SelectQueryBuilder(entityClass)
            builder.where {
                idProperty eq (id as Any)
            }
            val query = builder.build()

            val results = executor.executeQuery(query)
            if (results.isNotEmpty()) {
                mapper.mapToEntity(results.first(), entityClass)
            } else {
                null
            }
        }
        return java.util.Optional.ofNullable(result)
    }

    override fun existsById(id: ID): Boolean {
        return findById(id).isPresent
    }

    override fun findAll(): List<T> {
        return withExecutor { executor ->
            val builder = SelectQueryBuilder(entityClass)
            val query = builder.build()
            val results = executor.executeQuery(query)
            results.map { mapper.mapToEntity(it, entityClass) }
        }
    }

    override fun findAllById(ids: Iterable<ID>): List<T> {
        return withExecutor { executor ->
            val idProperty = getIdProperty()
            val builder = SelectQueryBuilder(entityClass)
            builder.where {
                idProperty inList ids.map { it as Any }
            }
            val query = builder.build()

            val results = executor.executeQuery(query)
            results.map { mapper.mapToEntity(it, entityClass) }
        }
    }

    override fun count(): Long {
        return withExecutor { executor ->
            val builder = SelectQueryBuilder(entityClass)
            builder.select {
                countAll("count")
            }
            val query = builder.build()

            val results = executor.executeQuery(query)
            if (results.isNotEmpty()) {
                (results.first()["count"] as? Number)?.toLong() ?: 0L
            } else {
                0L
            }
        }
    }

    override fun deleteById(id: ID) {
        withExecutor { executor ->
            val idProperty = getIdProperty()
            val builder = DeleteQueryBuilder(entityClass)
            builder.where {
                idProperty eq (id as Any)
            }
            val query = builder.build()

            executor.executeDelete(query)
        }
    }

    override fun delete(entity: T) {
        val idProperty = getIdProperty()
        val idValue = idProperty.get(entity) as? ID
            ?: throw IllegalStateException("Entity ID cannot be null")

        deleteById(idValue)
    }

    override fun deleteAllById(ids: Iterable<ID>) {
        ids.forEach { deleteById(it) }
    }

    override fun deleteAll(entities: Iterable<T>) {
        entities.forEach { delete(it) }
    }

    override fun deleteAll() {
        withExecutor { executor ->
            val builder = DeleteQueryBuilder(entityClass)
            val query = builder.build()
            executor.executeDelete(query)
        }
    }

    // Métodos específicos do AggORM

    override fun findWhere(block: WhereBuilder<T>.() -> Predicate): List<T> {
        return withExecutor { executor ->
            val builder = SelectQueryBuilder(entityClass)
            builder.where(block)
            val query = builder.build()

            val results = executor.executeQuery(query)
            results.map { mapper.mapToEntity(it, entityClass) }
        }
    }

    override fun findOneWhere(block: WhereBuilder<T>.() -> Predicate): T? {
        val results = findWhere(block)
        return results.firstOrNull()
    }

    override fun countWhere(block: WhereBuilder<T>.() -> Predicate): Long {
        return withExecutor { executor ->
            val builder = SelectQueryBuilder(entityClass)
            builder.select {
                countAll("count")
            }
            builder.where(block)
            val query = builder.build()

            val results = executor.executeQuery(query)
            if (results.isNotEmpty()) {
                (results.first()["count"] as? Number)?.toLong() ?: 0L
            } else {
                0L
            }
        }
    }

    override fun deleteWhere(block: WhereBuilder<T>.() -> Predicate): Int {
        return withExecutor { executor ->
            val builder = DeleteQueryBuilder(entityClass)
            builder.where(block)
            val query = builder.build()

            executor.executeDelete(query)
        }
    }
}
