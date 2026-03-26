package com.rytmo.library.persistence.devicetokens

import com.rytmo.library.persistence.CompositeKeyBaseDao
import com.rytmo.library.persistence.PaginationTokenEncryptor
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchema
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchemaParams
import java.lang.invoke.MethodHandles

class DeviceTokenDao(dynamoDbEnhancedClient: DynamoDbEnhancedClient, paginationTokenEncryptor: PaginationTokenEncryptor, tableName: String) :
    CompositeKeyBaseDao<DeviceTokenDynamoDbBean>(
        dynamoDbEnhancedClient,
        dynamoDbEnhancedClient.table(
            tableName,
            BeanTableSchema.create(
                BeanTableSchemaParams.builder(DeviceTokenDynamoDbBean::class.java)
                    .lookup(
                        MethodHandles.privateLookupIn(
                            DeviceTokenDynamoDbBean::class.java,
                            MethodHandles.lookup(),
                        ),
                    )
                    .build(),
            ),
        ),
        paginationTokenEncryptor,
    )
