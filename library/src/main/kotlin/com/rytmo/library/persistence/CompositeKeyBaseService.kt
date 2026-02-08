package com.rytmo.library.persistence

abstract class CompositeKeyBaseService<T : CompositeKeyPersistable>(protected val dao: CompositeKeyBaseDao<T>) {
    fun create(entity: T): T = dao.create(entity)

    fun get(partitionKey: String, sortKey: String): T? = dao.get(partitionKey, sortKey)

    fun update(entity: T): T = dao.update(entity)

    fun delete(partitionKey: String, sortKey: String) = dao.delete(partitionKey, sortKey)

    fun listByPartitionKey(partitionKey: String): List<T> = dao.listByPartitionKey(partitionKey)

    fun scan(limit: Int = 20, nextPageToken: String? = null): PagedResult<T> = dao.scan(limit, nextPageToken)
}
