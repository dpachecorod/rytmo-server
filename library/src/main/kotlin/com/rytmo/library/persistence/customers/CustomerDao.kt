package com.rytmo.library.persistence.customers

import com.rytmo.library.persistence.BaseDao
import com.rytmo.library.persistence.PaginationTokenEncryptor
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchema
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchemaParams
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import java.lang.invoke.MethodHandles

open class CustomerDao(dynamoDbEnhancedClient: DynamoDbEnhancedClient, paginationTokenEncryptor: PaginationTokenEncryptor, tableName: String) :
    BaseDao<CustomerDynamoDbBean>(
        dynamoDbEnhancedClient,
        dynamoDbEnhancedClient.table(
            tableName,
            BeanTableSchema.create(
                BeanTableSchemaParams.builder(CustomerDynamoDbBean::class.java)
                    .lookup(
                        MethodHandles.privateLookupIn(
                            CustomerDynamoDbBean::class.java,
                            MethodHandles.lookup(),
                        ),
                    )
                    .build(),
            ),
        ),
        paginationTokenEncryptor,
    ) {
    private val emailIndex = table.index("email-index")

    fun findByEmail(email: String): CustomerDynamoDbBean? {
        val queryConditional =
            QueryConditional.keyEqualTo(
                Key.builder().partitionValue(email).build(),
            )

        val request = QueryEnhancedRequest.builder().queryConditional(queryConditional).limit(1).build()

        return emailIndex.query(request).flatMap { it.items() }.firstOrNull()
    }

    fun existsByEmail(email: String): Boolean = findByEmail(email) != null
}
