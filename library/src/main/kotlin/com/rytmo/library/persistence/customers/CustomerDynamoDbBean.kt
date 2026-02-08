package com.rytmo.library.persistence.customers

import com.rytmo.library.persistence.Persistable
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey
import java.time.Instant

@DynamoDbBean
class CustomerDynamoDbBean : Persistable {
    @get:DynamoDbPartitionKey override var id: String = ""

    @get:DynamoDbSecondaryPartitionKey(indexNames = ["email-index"])
    @get:DynamoDbAttribute("email")
    var email: String = ""

    @get:DynamoDbAttribute("name")
    var name: String = ""

    @get:DynamoDbAttribute("createdAt")
    override var createdAt: Instant? = null

    @get:DynamoDbAttribute("updatedAt")
    override var updatedAt: Instant? = null

    @get:DynamoDbAttribute("ttl")
    override var ttl: Long? = null
}
