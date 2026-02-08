package com.rytmo.library.persistence.customeridentities

import com.rytmo.library.persistence.CompositeKeyPersistable
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import java.time.Instant

@DynamoDbBean
class CustomerIdentityDynamoDbBean : CompositeKeyPersistable {
    @get:DynamoDbPartitionKey
    @get:DynamoDbAttribute("internalCustomerId")
    var internalCustomerId: String = ""

    @get:DynamoDbSortKey
    @get:DynamoDbAttribute("provider")
    var provider: String = ""

    @get:DynamoDbSecondaryPartitionKey(indexNames = ["externalId-index"])
    @get:DynamoDbAttribute("externalId")
    var externalId: String = ""

    @get:DynamoDbAttribute("createdAt")
    override var createdAt: Instant? = null

    @get:DynamoDbAttribute("updatedAt")
    override var updatedAt: Instant? = null

    @get:DynamoDbAttribute("ttl")
    override var ttl: Long? = null

    @get:DynamoDbIgnore
    override var partitionKey: String
        get() = internalCustomerId
        set(value) {
            internalCustomerId = value
        }

    @get:DynamoDbIgnore
    override var sortKey: String
        get() = provider
        set(value) {
            provider = value
        }
}
