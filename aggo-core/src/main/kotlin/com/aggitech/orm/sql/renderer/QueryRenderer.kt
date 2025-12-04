package com.aggitech.orm.sql.renderer

import com.aggitech.orm.query.model.Query
import com.aggitech.orm.sql.context.RenderedSql

/**
 * Interface para renderização de queries SQL
 */
interface QueryRenderer<T : Query> {
    /**
     * Renderiza uma query em SQL + parâmetros
     */
    fun render(query: T): RenderedSql
}
