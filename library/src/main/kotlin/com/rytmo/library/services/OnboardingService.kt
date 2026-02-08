package com.rytmo.library.services

import com.rytmo.library.exceptions.EmailAlreadyExistsException
import com.rytmo.library.mappers.CustomerMapper
import com.rytmo.library.persistence.customeridentities.CustomerIdentityService
import com.rytmo.library.persistence.customers.CustomerDao
import com.rytmo.library.persistence.customers.CustomerService
import com.rytmo.models.customer.Customer

class OnboardingService(private val customerService: CustomerService, private val customerIdentityService: CustomerIdentityService, private val customerDao: CustomerDao) {
    companion object {
        const val PROVIDER_PRIVY = "privy"
        val customerMapper: CustomerMapper = CustomerMapper.INSTANCE
    }

    fun onboard(email: String, name: String, privyUserId: String): Customer {
        if (customerDao.existsByEmail(email)) {
            throw EmailAlreadyExistsException(email)
        }

        val customer = customerService.createCustomer(email, name)

        customerIdentityService.linkIdentity(
            internalCustomerId = customer.id,
            provider = PROVIDER_PRIVY,
            externalId = privyUserId,
        )

        return customerMapper.map(customer)
    }

    fun getCustomerByExternalId(externalId: String): Customer? {
        val internalCustomerId =
            customerIdentityService.getInternalCustomerIdByExternalId(externalId) ?: return null
        val customer = customerService.get(internalCustomerId) ?: return null
        return customer.let { customerMapper.map(it) }
    }
}
