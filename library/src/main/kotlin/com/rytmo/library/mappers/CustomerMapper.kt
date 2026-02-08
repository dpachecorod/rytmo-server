package com.rytmo.library.mappers

import com.rytmo.library.persistence.customers.CustomerDynamoDbBean
import com.rytmo.models.customer.Customer
import org.mapstruct.Mapper
import org.mapstruct.factory.Mappers

@Mapper
interface CustomerMapper {

    companion object {
        val INSTANCE: CustomerMapper
            get() = Mappers.getMapper(CustomerMapper::class.java)
    }

    fun map(customerDynamoDbBean: CustomerDynamoDbBean): Customer
}
