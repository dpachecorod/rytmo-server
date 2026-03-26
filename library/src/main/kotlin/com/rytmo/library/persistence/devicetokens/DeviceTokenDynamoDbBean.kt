package com.rytmo.library.persistence.devicetokens

import com.rytmo.library.persistence.CompositeKeyPersistable
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import java.time.Instant

@DynamoDbBean
class DeviceTokenDynamoDbBean : CompositeKeyPersistable {
    @get:DynamoDbPartitionKey
    @get:DynamoDbAttribute("customerId")
    var customerId: String = ""

    @get:DynamoDbSortKey
    @get:DynamoDbAttribute("tokenHash")
    var tokenHash: String = ""

    @get:DynamoDbAttribute("expoToken")
    var expoToken: String = ""

    @get:DynamoDbAttribute("createdAt")
    override var createdAt: Instant? = null

    @get:DynamoDbAttribute("updatedAt")
    override var updatedAt: Instant? = null

    @get:DynamoDbAttribute("ttl")
    override var ttl: Long? = null

    @get:DynamoDbIgnore
    override var partitionKey: String
        get() = customerId
        set(value) {
            customerId = value
        }

    @get:DynamoDbIgnore
    override var sortKey: String
        get() = tokenHash
        set(value) {
            tokenHash = value
        }
}
