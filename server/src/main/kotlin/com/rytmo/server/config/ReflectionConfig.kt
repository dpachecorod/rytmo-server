package com.rytmo.server.config

import com.rytmo.library.persistence.customeridentities.CustomerIdentityDynamoDbBean
import com.rytmo.library.persistence.customers.CustomerDynamoDbBean
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection(
    targets =
    [
        CustomerDynamoDbBean::class,
        CustomerIdentityDynamoDbBean::class,
    ],
)
class ReflectionConfig
