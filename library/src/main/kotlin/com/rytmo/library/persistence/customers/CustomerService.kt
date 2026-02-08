package com.rytmo.library.persistence.customers

import com.rytmo.library.persistence.BaseService

class CustomerService(customerDao: CustomerDao) : BaseService<CustomerDynamoDbBean>(customerDao) {
    fun createCustomer(email: String, name: String): CustomerDynamoDbBean {
        val customer = CustomerDynamoDbBean()
        customer.email = email
        customer.name = name
        return create(customer)
    }

    fun updateCustomer(id: String, email: String?, name: String?): CustomerDynamoDbBean? {
        val existingCustomer = get(id) ?: return null

        email?.let { existingCustomer.email = it }
        name?.let { existingCustomer.name = it }

        return update(existingCustomer)
    }
}
