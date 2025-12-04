package com.aggitech.orm.spring.transaction

import com.aggitech.orm.config.DbConfig
import com.aggitech.orm.config.JdbcConnectionFactory
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionSystemException
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.ResourceTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Connection

/**
 * TransactionManager do Spring para AggORM
 * Permite usar @Transactional com AggORM
 */
class AggoTransactionManager(
    private val dbConfig: DbConfig
) : AbstractPlatformTransactionManager(), ResourceTransactionManager {

    private val connectionFactory = JdbcConnectionFactory(dbConfig)

    override fun getResourceFactory(): Any {
        return dbConfig
    }

    override fun doGetTransaction(): Any {
        val holder = TransactionSynchronizationManager.getResource(dbConfig) as? ConnectionHolder
        return AggoTransactionObject(holder)
    }

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        val txObject = transaction as AggoTransactionObject

        if (txObject.connectionHolder == null) {
            val connection = connectionFactory.open()
            connection.autoCommit = false

            // Configura isolation level
            when (definition.isolationLevel) {
                TransactionDefinition.ISOLATION_READ_UNCOMMITTED ->
                    connection.transactionIsolation = Connection.TRANSACTION_READ_UNCOMMITTED
                TransactionDefinition.ISOLATION_READ_COMMITTED ->
                    connection.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED
                TransactionDefinition.ISOLATION_REPEATABLE_READ ->
                    connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
                TransactionDefinition.ISOLATION_SERIALIZABLE ->
                    connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE
            }

            val holder = ConnectionHolder(connection)
            holder.setSynchronizedWithTransaction(true)

            txObject.connectionHolder = holder
            TransactionSynchronizationManager.bindResource(dbConfig, holder)
        }
    }

    override fun doCommit(status: DefaultTransactionStatus) {
        val txObject = status.transaction as AggoTransactionObject
        val connection = txObject.connectionHolder?.connection
            ?: throw CannotCreateTransactionException("No connection found for transaction")

        try {
            connection.commit()
        } catch (ex: Exception) {
            throw TransactionSystemException("Could not commit JDBC transaction", ex)
        }
    }

    override fun doRollback(status: DefaultTransactionStatus) {
        val txObject = status.transaction as AggoTransactionObject
        val connection = txObject.connectionHolder?.connection
            ?: throw CannotCreateTransactionException("No connection found for transaction")

        try {
            connection.rollback()
        } catch (ex: Exception) {
            throw TransactionSystemException("Could not rollback JDBC transaction", ex)
        }
    }

    override fun doCleanupAfterCompletion(transaction: Any) {
        val txObject = transaction as AggoTransactionObject

        TransactionSynchronizationManager.unbindResource(dbConfig)

        txObject.connectionHolder?.connection?.let { connection ->
            try {
                connection.autoCommit = true
                connection.close()
            } catch (ex: Exception) {
                logger.debug("Could not close JDBC connection after transaction", ex)
            }
        }

        txObject.connectionHolder = null
    }

    /**
     * Obtém a conexão atual da thread
     */
    fun getCurrentConnection(): Connection? {
        val holder = TransactionSynchronizationManager.getResource(dbConfig) as? ConnectionHolder
        return holder?.connection
    }
}

/**
 * Objeto de transação do AggORM
 */
class AggoTransactionObject(
    var connectionHolder: ConnectionHolder? = null
)

/**
 * Holder para Connection
 */
class ConnectionHolder(
    val connection: Connection
) {
    private var synchronizedWithTransaction = false

    fun setSynchronizedWithTransaction(value: Boolean) {
        synchronizedWithTransaction = value
    }

    fun isSynchronizedWithTransaction(): Boolean {
        return synchronizedWithTransaction
    }
}

/**
 * Extension para usar a conexão transacional nos repositories
 */
fun AggoTransactionManager.getConnectionOrCreate(): Connection {
    return getCurrentConnection() ?: JdbcConnectionFactory(
        // Este é um fallback - idealmente sempre deve haver uma transação ativa
        DbConfig(
            database = "default",
            host = "localhost",
            port = 5432,
            user = "postgres",
            password = ""
        )
    ).open()
}
