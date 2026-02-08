package com.rytmo.library.persistence.customeridentities

import com.rytmo.library.persistence.CompositeKeyBaseDao
import com.rytmo.library.persistence.PaginationTokenEncryptor
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchema
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchemaParams
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import java.lang.invoke.MethodHandles

class CustomerIdentityDao(dynamoDbEnhancedClient: DynamoDbEnhancedClient, paginationTokenEncryptor: PaginationTokenEncryptor, tableName: String) :
    CompositeKeyBaseDao<CustomerIdentityDynamoDbBean>(
        dynamoDbEnhancedClient,
        dynamoDbEnhancedClient.table(
            tableName,
            BeanTableSchema.create(
                BeanTableSchemaParams.builder(CustomerIdentityDynamoDbBean::class.java)
                    .lookup(
                        MethodHandles.privateLookupIn(
                            CustomerIdentityDynamoDbBean::class.java,
                            MethodHandles.lookup(),
                        ),
                    )
                    .build(),
            ),
        ),
        paginationTokenEncryptor,
    ) {
    private val externalIdIndex = table.index("externalId-index")

    fun findByExternalId(externalId: String): CustomerIdentityDynamoDbBean? {
        val queryConditional =
            QueryConditional.keyEqualTo(
                Key.builder().partitionValue(externalId).build(),
            )

        val request = QueryEnhancedRequest.builder().queryConditional(queryConditional).limit(1).build()

        return externalIdIndex.query(request).flatMap { it.items() }.firstOrNull()
    }
}
