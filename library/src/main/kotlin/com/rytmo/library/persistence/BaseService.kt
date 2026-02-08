package com.rytmo.library.persistence

abstract class BaseService<T : Persistable>(protected val dao: BaseDao<T>) {
    fun create(entity: T): T = dao.create(entity)

    fun get(id: String): T? = dao.get(id)

    fun update(entity: T): T = dao.update(entity)

    fun delete(id: String) = dao.delete(id)

    fun list(limit: Int = 20, nextPageToken: String? = null): PagedResult<T> = dao.list(limit, nextPageToken)
}
