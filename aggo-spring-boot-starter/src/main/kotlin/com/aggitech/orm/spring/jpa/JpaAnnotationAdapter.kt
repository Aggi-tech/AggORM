package com.aggitech.orm.spring.jpa

import com.aggitech.orm.core.metadata.EntityRegistry
import jakarta.persistence.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Adaptador que permite ao AggORM ler anotações JPA padrão
 * Mantém o core independente enquanto suporta integração com JPA
 */
object JpaAnnotationAdapter {

    /**
     * Resolve o nome da tabela a partir de anotações JPA
     * Suporta: @Entity, @Table
     */
    fun <T : Any> resolveTableName(kClass: KClass<T>): String {
        // Verifica @Table primeiro
        kClass.findAnnotation<Table>()?.let { table ->
            if (table.name.isNotEmpty()) {
                return table.name
            }
        }

        // Depois verifica @Entity
        kClass.findAnnotation<Entity>()?.let { entity ->
            if (entity.name.isNotEmpty()) {
                return entity.name
            }
        }

        // Fallback para EntityRegistry padrão (conversão para snake_case)
        return EntityRegistry.resolveTable(kClass)
    }

    /**
     * Resolve o nome da coluna a partir de anotações JPA
     * Suporta: @Column
     */
    fun resolveColumnName(property: KProperty1<*, *>): String {
        property.findAnnotation<Column>()?.let { column ->
            if (column.name.isNotEmpty()) {
                return column.name
            }
        }

        // Fallback para EntityRegistry padrão (conversão para snake_case)
        return EntityRegistry.resolveColumn(property)
    }

    /**
     * Verifica se uma propriedade é chave primária
     * Suporta: @Id
     */
    fun isPrimaryKey(property: KProperty1<*, *>): Boolean {
        return property.findAnnotation<Id>() != null
    }

    /**
     * Verifica se uma propriedade deve ser ignorada
     * Suporta: @Transient
     */
    fun isTransient(property: KProperty1<*, *>): Boolean {
        return property.findAnnotation<Transient>() != null
    }

    /**
     * Obtém a propriedade ID de uma entidade JPA
     */
    fun <T : Any> getIdProperty(kClass: KClass<T>): KProperty1<T, *>? {
        return kClass.memberProperties.firstOrNull { property ->
            isPrimaryKey(property as KProperty1<*, *>)
        } as? KProperty1<T, *>
    }

    /**
     * Verifica se uma classe é uma entidade JPA
     */
    fun isJpaEntity(kClass: KClass<*>): Boolean {
        return kClass.findAnnotation<Entity>() != null
    }

    /**
     * Obtém informações de relacionamento @ManyToOne
     */
    fun getManyToOneInfo(property: KProperty1<*, *>): ManyToOne? {
        return property.findAnnotation()
    }

    /**
     * Obtém informações de relacionamento @OneToMany
     */
    fun getOneToManyInfo(property: KProperty1<*, *>): OneToMany? {
        return property.findAnnotation()
    }

    /**
     * Obtém informações de relacionamento @OneToOne
     */
    fun getOneToOneInfo(property: KProperty1<*, *>): OneToOne? {
        return property.findAnnotation()
    }

    /**
     * Obtém informações de relacionamento @ManyToMany
     */
    fun getManyToManyInfo(property: KProperty1<*, *>): ManyToMany? {
        return property.findAnnotation()
    }

    /**
     * Obtém informações de coluna de join
     */
    fun getJoinColumnInfo(property: KProperty1<*, *>): JoinColumn? {
        return property.findAnnotation()
    }

    /**
     * Verifica se uma coluna é nullable
     */
    fun isNullable(property: KProperty1<*, *>): Boolean {
        property.findAnnotation<Column>()?.let {
            return it.nullable
        }
        return true // Default JPA é nullable
    }

    /**
     * Verifica se uma coluna é unique
     */
    fun isUnique(property: KProperty1<*, *>): Boolean {
        property.findAnnotation<Column>()?.let {
            return it.unique
        }
        return false
    }

    /**
     * Obtém o length de uma coluna String
     */
    fun getColumnLength(property: KProperty1<*, *>): Int {
        property.findAnnotation<Column>()?.let {
            return it.length
        }
        return 255 // Default JPA
    }

    /**
     * Obtém informações de GeneratedValue
     */
    fun getGeneratedValueInfo(property: KProperty1<*, *>): GeneratedValue? {
        return property.findAnnotation()
    }

    /**
     * Obtém schema da tabela
     */
    fun <T : Any> getSchema(kClass: KClass<T>): String? {
        kClass.findAnnotation<Table>()?.let { table ->
            if (table.schema.isNotEmpty()) {
                return table.schema
            }
        }
        return null
    }
}
