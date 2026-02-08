package com.rytmo.library.persistence

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.Page
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.Instant

abstract class CompositeKeyBaseDao<T : CompositeKeyPersistable>(
    protected val dynamoDbEnhancedClient: DynamoDbEnhancedClient,
    protected val table: DynamoDbTable<T>,
    protected val paginationTokenEncryptor: PaginationTokenEncryptor,
) {
    fun create(entity: T): T {
        val now = Instant.now()
        entity.createdAt = now
        entity.updatedAt = now
        table.putItem(entity)
        return entity
    }

    fun get(partitionKey: String, sortKey: String): T? {
        val key = Key.builder().partitionValue(partitionKey).sortValue(sortKey).build()
        return table.getItem(key)
    }

    fun update(entity: T): T {
        entity.updatedAt = Instant.now()
        table.updateItem(entity)
        return entity
    }

    fun delete(partitionKey: String, sortKey: String) {
        val key = Key.builder().partitionValue(partitionKey).sortValue(sortKey).build()
        table.deleteItem(key)
    }

    fun listByPartitionKey(partitionKey: String): List<T> {
        val queryConditional =
            QueryConditional.keyEqualTo(
                Key.builder().partitionValue(partitionKey).build(),
            )

        val request = QueryEnhancedRequest.builder().queryConditional(queryConditional).build()

        return table.query(request).flatMap { it.items() }
    }

    fun scan(limit: Int = 20, nextPageToken: String? = null): PagedResult<T> {
        val requestBuilder = ScanEnhancedRequest.builder().limit(limit)

        if (nextPageToken != null) {
            val decryptedKey = paginationTokenEncryptor.decrypt(nextPageToken)
            val exclusiveStartKey =
                decryptedKey.mapValues { entry -> AttributeValue.builder().s(entry.value).build() }
            requestBuilder.exclusiveStartKey(exclusiveStartKey)
        }

        val pages: Iterable<Page<T>> = table.scan(requestBuilder.build())
        val firstPage = pages.iterator().next()

        val items = firstPage.items()
        val lastEvaluatedKey = firstPage.lastEvaluatedKey()

        val encryptedToken =
            if (lastEvaluatedKey != null && lastEvaluatedKey.isNotEmpty()) {
                val keyMap =
                    lastEvaluatedKey.mapValues { entry -> entry.value.s() ?: entry.value.n() ?: "" }
                paginationTokenEncryptor.encrypt(keyMap)
            } else {
                null
            }

        return PagedResult(
            items = items,
            nextPageToken = encryptedToken,
        )
    }
}
